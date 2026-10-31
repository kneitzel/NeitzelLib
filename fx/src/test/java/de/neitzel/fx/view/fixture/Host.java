package de.neitzel.fx.view.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * A model whose visits a Table shows.
 */
public class Host {

    /**
     * The visits. After a view loads, this is the list the Table watches.
     */
    private List<Visit> visits = new ArrayList<>();

    /**
     * The selected visit, or {@code null} when none is selected.
     */
    private Visit selectedVisit;

    /**
     * Returns the visits.
     *
     * @return the visits
     */
    public List<Visit> getVisits() {
        return visits;
    }

    /**
     * Sets the visits.
     *
     * @param visits the visits
     */
    public void setVisits(List<Visit> visits) {
        this.visits = visits;
    }

    /**
     * Returns the selected visit.
     *
     * @return the selected visit, or {@code null}
     */
    public Visit getSelectedVisit() {
        return selectedVisit;
    }

    /**
     * Sets the selected visit.
     *
     * @param selectedVisit the selected visit
     */
    public void setSelectedVisit(Visit selectedVisit) {
        this.selectedVisit = selectedVisit;
    }
}
