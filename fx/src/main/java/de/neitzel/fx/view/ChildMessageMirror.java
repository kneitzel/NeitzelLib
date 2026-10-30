package de.neitzel.fx.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carries one child ViewModel's current notices onto its parent.
 * <p>
 * Each copy is keyed by the identity of the child {@link Message}. A later text change on that
 * child notice updates the same copy, and dropping the child notice drops the copy. The copy's
 * source is the Attribute name, a dot, and the child source. A further parent carries that copy
 * the same way, so the source grows into a Path. The copy is not logged again.
 */
final class ChildMessageMirror implements MessageListener {

    private final ViewModel parent;
    private final String attribute;
    private final ViewModel child;
    private final Map<Message, Message> copies = new IdentityHashMap<>();

    private ChildMessageMirror(ViewModel parent, String attribute, ViewModel child) {
        this.parent = parent;
        this.attribute = attribute;
        this.child = child;
    }

    /**
     * Starts carrying {@code child} onto {@code parent} under {@code attribute}.
     * <p>
     * Notices already on the child are copied, and notices added later are copied as they arrive.
     *
     * @param parent    the parent ViewModel
     * @param attribute the Attribute that leads to the child
     * @param child     the child ViewModel
     * @return the mirror, so it can be detached when the Attribute is cleared
     */
    static ChildMessageMirror attach(ViewModel parent, String attribute, ViewModel child) {
        ChildMessageMirror mirror = new ChildMessageMirror(parent, attribute, child);
        child.addMessageListener(mirror);
        for (Message message : List.copyOf(child.getMessages())) {
            mirror.carry(message);
        }
        return mirror;
    }

    /**
     * Stops carrying and drops this Attribute's copies from the parent.
     */
    void detach() {
        child.removeMessageListener(this);
        List<Message> dropped = new ArrayList<>(copies.values());
        copies.clear();
        for (Message copy : dropped) {
            parent.removeCarriedMessage(copy);
        }
    }

    /**
     * Copies {@code message} onto the parent unless this parent already holds it.
     *
     * @param message the child notice
     */
    @Override
    public void added(Message message) {
        carry(message);
    }

    /**
     * Drops the parent's copy of {@code message}.
     *
     * @param message the child notice
     */
    @Override
    public void removed(Message message) {
        Message copy = copies.remove(message);
        if (copy != null) {
            parent.removeCarriedMessage(copy);
        }
    }

    /**
     * Writes the child's new text onto the same parent copy.
     *
     * @param message the child notice
     */
    @Override
    public void textChanged(Message message) {
        Message copy = copies.get(message);
        if (copy == null) {
            return;
        }
        copy.setText(message.getText());
        parent.noteMessageText(copy);
    }

    private void carry(Message message) {
        if (copies.containsKey(message) || repeats(message)) {
            return;
        }
        Message copy = message.withExtendedSource(attribute + "." + message.getSource());
        copies.put(message, copy);
        parent.addCarriedMessage(copy);
    }

    /**
     * A cycle would carry a notice back onto a ViewModel that already holds it, or that holds
     * the notice it was copied from.
     *
     * @param message the child notice
     * @return {@code true} when the parent already has this notice or its origin
     */
    private boolean repeats(Message message) {
        Message current = message;
        Set<Message> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && seen.add(current)) {
            if (parent.getMessages().contains(current)) {
                return true;
            }
            current = current.origin();
        }
        return false;
    }
}
