package com.dji.myapplication;

import org.maplibre.android.maps.Style;

public class TestStyles {
    public static final String DEMOTILES = "https://demotiles.maplibre.org/style.json";

    public static final String VERSATILES = "https://tiles.versatiles.org/assets/styles/colorful.json";

    public static final String AMERICANA = "https://americanamap.org/style.json";

    public static final String OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty";

    public static final String OPENFREEMAP_BRIGHT = "https://tiles.openfreemap.org/styles/bright";

    public static final String AZZ = "https://api.mapbox.com/styles/v1/mapbox/satellite-v9?access_token=YOUR_MAPBOX_ACCESS_TOKEN";

    public static final String AWS_OPEN_DATA_STANDARD_LIGHT =
            "https://maps.geo.us-east-2.amazonaws.com/maps/v0/maps/OpenDataStyle/style-descriptor?key=v1.public.eyJqdGkiOiI1NjY5ZTU4My0yNWQwLTQ5MjctODhkMS03OGUxOTY4Y2RhMzgifR_7GLT66TNRXhZJ4KyJ-GK1TPYD9DaWuc5o6YyVmlikVwMaLvEs_iqkCIydspe_vjmgUVsIQstkGoInXV_nd5CcmqRMMa-_wb66SxDdbeRDvmmkpy2Ow_LX9GJDgL2bbiCws0wupJPFDwWCWFLwpK9ICmzGvNcrPbX5uczOQL0N8V9iUvziA52a1WWkZucIf6MUViFRf3XoFkyAT15Ll0NDypAzY63Bnj8_zS8bOaCvJaQqcXM9lrbTusy8Ftq8cEbbK5aMFapXRjug7qcrzUiQ5sr0g23qdMvnKJQFfo7JuQn8vwAksxrQm6A0ByceEXSfyaBoVpFcTzEclxUomhY.NjAyMWJkZWUtMGMyOS00NmRkLThjZTMtODEyOTkzZTUyMTBi";

    private static String protomaps(String style) {
        return "https://api.protomaps.com/styles/v2/" + style + ".json?key=e761cc7daedf832a";
    }

    public static final String PROTOMAPS_LIGHT = protomaps("light");
    public static final String PROTOMAPS_DARK = protomaps("dark");
    public static final String PROTOMAPS_GRAYSCALE = protomaps("grayscale");
    public static final String PROTOMAPS_WHITE = protomaps("white");
    public static final String PROTOMAPS_BLACK = protomaps("black");

    public static String getPredefinedStyleWithFallback(String name) {
        try {
            // Assuming Style is a class available in your project
            return Style.getPredefinedStyle(name);
        } catch (Exception e) {
            return VERSATILES;
        }
    }
}
