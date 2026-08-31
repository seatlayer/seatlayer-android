package io.seatlayer.consumer.raw;

import io.seatlayer.android.SeatLayerConfiguration;
import io.seatlayer.android.SeatLayerController;
import io.seatlayer.android.SeatLayerView;

public final class RawJavaConsumer {
    private RawJavaConsumer() {}

    public static SeatLayerController controller(SeatLayerView view) {
        return view.getController();
    }

    public static Class<SeatLayerConfiguration> configurationType() {
        return SeatLayerConfiguration.class;
    }
}
