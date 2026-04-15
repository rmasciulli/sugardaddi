package li.masciul.sugardaddi.data.sources.ciqual.xml;

import org.simpleframework.xml.transform.Transform;

/**
 * Custom transformer for doubles that handles whitespace and null values
 *
 * The Ciqual XML data contains doubles with:
 * - Leading/trailing spaces (e.g., "  2.5  ")
 * - French decimal format with commas (e.g., "2,5")
 * - Empty values
 * - Malformed "missing" attributes
 *
 * This transformer handles all these cases gracefully.
 */
public class SafeDoubleTransform implements Transform<Double> {

    @Override
    public Double read(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String cleaned = value.trim();

        // Handle French decimal format (comma instead of period)
        cleaned = cleaned.replace(',', '.');

        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException e) {
            // If parse fails, return null instead of throwing
            return null;
        }
    }

    @Override
    public String write(Double value) throws Exception {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}