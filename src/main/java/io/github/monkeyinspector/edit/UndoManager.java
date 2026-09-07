package io.github.monkeyinspector.edit;

import java.util.ArrayDeque;
import java.util.Deque;

/** Worker-confined bounded history. Entries move only after successful restoration. */
public final class UndoManager {
    private final Deque<EditTransaction> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final int capacity;
    public UndoManager(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public void push(EditTransaction entry) {
        undo.push(entry); redo.clear();
        while (undo.size() > capacity) undo.removeLast();
    }
    public EditTransaction peek(boolean forward) { return (forward ? redo : undo).peek(); }
    public void moved(boolean forward) { (forward ? undo : redo).push((forward ? redo : undo).pop()); }
    public int undoCount() { return undo.size(); }
    public int redoCount() { return redo.size(); }
}
