/* Shared property and viewport transaction client. No source rewriting in the browser. */
(() => {
    const el = id => document.getElementById(id)
    let data = null, editableId = '', tool = 'select', polling = false, editing = false
    let dragSession = null, pointer = null, chain = Promise.resolve(), previewBusy = false
    const message = text => { el('editMessage').textContent = text }
    async function request(path, body) {
        const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)})
        const result = await response.json()
        if (!response.ok) throw new Error(result.message || result.status || `HTTP ${response.status}`)
        return result
    }
    const binding = () => data?.bindings.find(b => b.id === editableId)
    const object = () => data?.objects.find(o => o.id === editableId)
    const mode = () => el('editMode').value
    function select(id, synchronize = true) {
        if (editing || pointer !== null) return
        editableId = id || ''
        el('editableSelect').value = editableId
        const item = object()
        if (item && synchronize) { selectedId = item.sceneId; renderScene(); renderDetails() }
        if (data?.viewport) request('/api/viewport/select', {editableId}).catch(e => message(e.message))
        renderProperties(true); overlay()
    }
    window.selectEditableForScene = item => select(item.editableId || data?.objects.find(o => o.sceneId === item.id)?.id || '', false)
    el('editableSelect').onchange = event => select(event.target.value)
    el('editMode').onchange = () => renderProperties(true)
    function renderProperties(force = false) {
        if (!data || editing || pointer !== null || (!force && el('transformEditor').contains(document.activeElement))) return
        const b = binding(), o = object(), root = el('transformEditor')
        root.replaceChildren()
        if (!editableId) { root.textContent = 'Select an editable object.'; el('sourceCode').textContent = 'Select a source binding.'; return }
        const values = mode() === 'SOURCE' ? b?.values : o?.values
        for (const [property, label, count] of [['localTranslation','Position',3],['localRotation','Rotation (quaternion)',4],['localScale','Scale',3]]) {
            const title = document.createElement('div'); title.textContent = label; root.append(title)
            const row = document.createElement('form'); row.className = 'transform-row'
            const inputs = []
            for (let i = 0; i < count; i++) {
                const labelEl = document.createElement('label'); labelEl.textContent = 'XYZW'[i]
                const input = document.createElement('input'); input.type = 'number'; input.step = 'any'; input.required = true
                input.value = values?.[property]?.[i] ?? ''; input.setAttribute('aria-label', `${label} ${'XYZW'[i]}`)
                labelEl.append(input); row.append(labelEl); inputs.push(input)
            }
            const locked = mode() !== 'LIVE' && !b?.values[property]
            const unavailable = mode() !== 'SOURCE' && !o
            const save = document.createElement('button'); save.textContent = 'Apply'; save.disabled = locked || unavailable
            inputs.forEach(input => { input.disabled = save.disabled })
            row.append(save); root.append(row)
            if (locked) { const status = document.createElement('div'); status.className = 'small'; status.textContent = b ? 'Computed / unsupported source · Runtime editable · Source editing locked' : 'Runtime only · No source binding'; root.append(status) }
            const revision = b?.revision // Keep the revision which produced these fields, even during background polling.
            row.onsubmit = async event => {
                event.preventDefault(); if (editing) return
                const value = inputs.map(input => Number(input.value))
                if (inputs.some(input => input.value === '') || !value.every(Number.isFinite)) return
                editing = true; save.disabled = true; message('Modified')
                try { const result = await request('/api/edit', {editableId, property, value, mode:mode(), expectedSourceRevision:revision}); message(result.message) }
                catch (e) { message(e.message) }
                finally { editing = false; await poll(); renderProperties(true); tick() }
            }
        }
        el('sourceCode').textContent = b ? `${b.file} · ${b.revision.slice(0,12)}\n` + b.code.split('\n').map((line,i) => `${b.startLine + i}  ${line}`).join('\n') : 'Runtime only — no source binding.'
    }
    async function poll() {
        if (polling) return
        polling = true
        try {
            const response = await fetch('/api/editor', {cache:'no-store'})
            if (!response.ok) throw new Error(`HTTP ${response.status}`)
            const first = !data; data = await response.json()
            if (first && data.mode === 'STATIC') el('editMode').value = 'SOURCE'
            el('connectionMode').textContent = data.mode
            el('connectionMode').title = data.mode === 'LIVE' ? 'Game connected' : 'Game disconnected · Source editing available'
            el('undoEdit').disabled = !data.undoCount || editing || pointer !== null
            el('redoEdit').disabled = !data.redoCount || editing || pointer !== null
            el('toolMove').disabled = !data.viewport
            el('viewportHint').textContent = data.viewport ? 'Select · Q / Move · W' : 'Game disconnected / viewport disabled'
            if (data.viewport && !el('gameFrame').getAttribute('src')) el('gameFrame').src = '/api/viewport/mjpeg'
            const ids = [...new Set([...data.objects.map(o => o.id), ...data.bindings.map(b => b.id)])]
            const selectEl = el('editableSelect')
            if (JSON.stringify(ids) !== selectEl.dataset.ids) {
                selectEl.replaceChildren(new Option('Select editable', ''), ...ids.map(id => new Option(id, id)))
                selectEl.dataset.ids = JSON.stringify(ids); selectEl.value = editableId
            }
            if (data.sourceError) message(data.sourceError)
            renderProperties(); overlay()
        } catch (e) { el('connectionMode').textContent = 'DISCONNECTED'; message('Inspector disconnected. Reconnect or start StaticInspector to edit sources.') }
        finally { polling = false }
    }
    for (const action of ['undo','redo']) el(action + 'Edit').onclick = async () => {
        if (editing || pointer !== null) return
        editing = true
        try { const result = await request('/api/' + action, {}); message(result.message) }
        catch (e) { message(e.message) }
        finally { editing = false; await poll(); tick() }
    }
    function setTool(value) {
        tool = value
        el('toolSelect').classList.toggle('tool-active', value === 'select')
        el('toolMove').classList.toggle('tool-active', value === 'move')
        el('editorOverlay').style.cursor = value === 'move' ? 'move' : 'crosshair'
    }
    el('toolSelect').onclick = () => setTool('select')
    el('toolMove').onclick = () => setTool('move')
    document.addEventListener('keydown', event => {
        if (event.target.matches('input,select,textarea') || event.ctrlKey || event.metaKey || event.altKey) return
        if (event.key.toLowerCase() === 'q') setTool('select')
        if (event.key.toLowerCase() === 'w' && data?.viewport) setTool('move')
        if (event.key === 'Escape' && pointer !== null) finish(null, true)
    })
    const canvas = el('editorOverlay'), frame = el('gameFrame')
    function overlay() {
        const parent = frame.parentElement.getBoundingClientRect()
        const ratio = frame.naturalWidth && frame.naturalHeight ? frame.naturalWidth / frame.naturalHeight : 16/9
        const width = Math.min(parent.width, parent.height * ratio), height = width / ratio
        canvas.style.left = `${(parent.width-width)/2}px`; canvas.style.top = `${(parent.height-height)/2}px`
        canvas.style.width = `${width}px`; canvas.style.height = `${height}px`
        canvas.width = Math.max(1,Math.round(width)); canvas.height = Math.max(1,Math.round(height))
        const ctx = canvas.getContext('2d'), point = object()?.screen
        if (point && point[2] >= 0 && point[2] <= 1) {
            const x = point[0]*width, y = point[1]*height
            ctx.strokeStyle = '#77caff'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(x,y,12,0,Math.PI*2); ctx.stroke()
            ctx.beginPath(); ctx.moveTo(x-22,y); ctx.lineTo(x+22,y); ctx.moveTo(x,y-22); ctx.lineTo(x,y+22); ctx.stroke()
            ctx.fillStyle = '#c8eaff'; ctx.font = '12px sans-serif'; ctx.fillText(editableId,x+17,y-16)
        }
    }
    new ResizeObserver(overlay).observe(frame.parentElement); frame.onload = overlay
    function coordinates(event) {
        const rect = canvas.getBoundingClientRect()
        return {x:Math.max(0,Math.min(1,(event.clientX-rect.left)/rect.width)), y:Math.max(0,Math.min(1,(event.clientY-rect.top)/rect.height))}
    }
    canvas.onpointerdown = event => {
        if (event.button !== 0 || pointer !== null || editing || !data?.viewport) return
        const point = coordinates(event)
        pointer = event.pointerId; canvas.setPointerCapture(pointer)
        chain = chain.then(async () => {
            if (tool === 'select' || !editableId) {
                const result = await request('/api/viewport/pick', point)
                const saved = pointer; pointer = null; select(result.editableId); pointer = saved
                return
            }
            const result = await request('/api/viewport/drag/begin', {...point, editableId, mode:mode(), expectedSourceRevision:binding()?.revision})
            dragSession = result.sessionId; message('Modified · dragging')
        }).catch(e => message(e.message))
    }
    canvas.onpointermove = event => {
        if (event.pointerId !== pointer || previewBusy) return
        const point = coordinates(event); previewBusy = true
        chain = chain.then(async () => {
            if (dragSession) await request('/api/viewport/drag/preview', {...point, sessionId:dragSession})
        }).catch(e => message(e.message)).finally(() => { previewBusy = false })
    }
    function finish(event, cancel) {
        if (pointer === null) return
        const point = event ? coordinates(event) : {}
        const oldPointer = pointer; pointer = null; editing = true
        if (canvas.hasPointerCapture(oldPointer)) canvas.releasePointerCapture(oldPointer)
        chain = chain.then(async () => {
            if (dragSession) {
                const result = await request('/api/viewport/drag/' + (cancel ? 'cancel' : 'commit'), {...point, sessionId:dragSession})
                message(result.message)
            }
        }).catch(e => message(e.message)).finally(async () => { dragSession = null; editing = false; await poll(); renderProperties(true); tick() })
    }
    canvas.onpointerup = event => { if (event.pointerId === pointer) finish(event, false) }
    canvas.onpointercancel = event => { if (event.pointerId === pointer) finish(event, true) }
    window.addEventListener('blur', () => { if (pointer !== null) finish(null, true) })
    setTool('select'); poll(); setInterval(poll, 500)
})()
