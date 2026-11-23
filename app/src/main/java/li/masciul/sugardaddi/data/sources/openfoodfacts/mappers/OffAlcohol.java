package li.masciul.sugardaddi.data.sources.openfoodfacts.mappers;

/**
 * OffAlcohol - Open Food Facts alcohol values into grams.
 *
 * OFF's alcohol_100g is always the strength in "% vol" (OFF product nutrition
 * schema), the figure printed on the label: millilitres of ethanol per 100 ml of
 * drink. The app stores alcohol in grams per
 * 100 g or 100 ml, like Ciqual and USDA. So:
 *
 *   g per 100 ml = % vol x density of ethanol (0.789 g/ml at 20 degrees C, the
 *   temperature the EU strength by volume is measured at).
 *
 * A strength is a concentration, not an amount: OFF gives the same number in
 * alcohol_100g and alcohol_serving, so it is never scaled by a serving.
 *
 * Only for products per 100 ml. Per 100 g, the drink's own density would be
 * needed, and OFF does not give it: the value is left empty rather than guessed.
 *
 * Pure Java on purpose (no Android types) so it can be unit tested.
 */
public final class OffAlcohol {

    /** Density of ethanol at 20 degrees C, in g/ml. */
    static final double ETHANOL_DENSITY_G_PER_ML = 0.789;

    private OffAlcohol() { /* no instances */ }

    /**
     * Grams of alcohol per 100 ml from a strength in % vol.
     *
     * @param percentVol strength as given by OFF (alcohol_100g)
     * @param perVolume  true when the product's values are per 100 ml
     * @return grams per 100 ml, or null when unknown, out of range or per 100 g
     */
    public static Double gramsPer100ml(Double percentVol, boolean perVolume) {
        if (percentVol == null || !perVolume) return null;
        if (percentVol < 0 || percentVol > 100
                || percentVol.isNaN() || percentVol.isInfinite()) {
            return null;
        }
        return percentVol * ETHANOL_DENSITY_G_PER_ML;
    }
}
