package li.masciul.sugardaddi.data.sources.ciqual.xml;

import org.simpleframework.xml.transform.Transform;

/**
 * Custom transformer for integers that handles whitespace
 *
 * The Ciqual XML data contains integers with leading/trailing spaces
 * like " 1000 " which the default IntegerTransform cannot parse.
 * This transformer trims the whitespace before parsing.
 */
public class SafeIntTransform implements Transform<Integer> {

    @Override
    public Integer read(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    @Override
    public String write(Integer value) throws Exception {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}