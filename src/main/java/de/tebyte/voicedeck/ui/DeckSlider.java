package de.tebyte.voicedeck.ui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

final class DeckSlider extends AbstractSliderButton {
    private final double minimum, maximum;
    private final DoubleConsumer changed;
    private final DoubleFunction<Component> label;
    DeckSlider(int x, int y, int width, double current, double minimum, double maximum,
               DoubleFunction<Component> label, DoubleConsumer changed) {
        super(x, y, width, 20, label.apply(current), (current - minimum) / (maximum - minimum));
        this.minimum = minimum; this.maximum = maximum; this.label = label; this.changed = changed;
    }
    @Override protected void updateMessage() { setMessage(label.apply(minimum + value * (maximum - minimum))); }
    @Override protected void applyValue() { changed.accept(minimum + value * (maximum - minimum)); }
}
