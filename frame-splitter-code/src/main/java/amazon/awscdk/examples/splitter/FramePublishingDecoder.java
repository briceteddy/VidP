package amazon.awscdk.examples.splitter;

import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.H264FrameDecoder;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * This class is an image saving wrapper for {@link com.amazonaws.kinesisvideo.parser.utilities.H264FrameDecoder}.
 */
public class FramePublishingDecoder extends H264FrameDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(FramePublishingDecoder.class);
    private final ImagePublisher imagePublisher;
    private final Consumer<Optional<FragmentMetadata>> callback;

    public FramePublishingDecoder(ImagePublisher bufferedImageConsumer, Consumer<Optional<FragmentMetadata>> callback) {
        this.imagePublisher = bufferedImageConsumer;
        this.callback = callback;
    }

    /**
     * Decodes received frame to {@link java.awt.image.BufferedImage} and publishes it using {@link amazon.awscdk.examples.splitter.ImagePublisher}
     * @param frame
     * @param trackMetadata
     * @param fragmentMetadata
     */
    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata, Optional<FragmentMetadata> fragmentMetadata) {
        LOG.info("Decoding frame: " + frame);
        BufferedImage bufferedImage = super.decodeH264Frame(frame, trackMetadata);
        LOG.info("Frame decoded: " + frame);

        imagePublisher.publish(bufferedImage);

        LOG.info("Invoking frame metadata callback");
        callback.accept(fragmentMetadata);
    }
}
