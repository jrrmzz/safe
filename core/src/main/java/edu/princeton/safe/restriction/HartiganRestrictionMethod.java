package edu.princeton.safe.restriction;

import edu.princeton.safe.RestrictionMethod;
import edu.princeton.safe.model.EnrichmentLandscape;

public class HartiganRestrictionMethod implements RestrictionMethod {

    public static final String ID = "hartigan";

    @Override
    public void applyRestriction(EnrichmentLandscape result) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public String getId() {
        return ID;
    }
}
