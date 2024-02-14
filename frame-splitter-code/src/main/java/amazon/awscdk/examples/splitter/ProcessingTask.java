package amazon.awscdk.examples.splitter;

import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.kinesisvideomedia.KinesisVideoMediaClient;
import software.amazon.awssdk.services.kinesisvideomedia.model.GetMediaRequest;
import software.amazon.awssdk.services.kinesisvideomedia.model.GetMediaResponse;
import software.amazon.awssdk.services.kinesisvideomedia.model.StartSelector;
import software.amazon.awssdk.services.kinesisvideomedia.model.StartSelectorType;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Class used to retrieve video stream using Consumer APIs and trigger processing
 */
public class ProcessingTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingTask.class);

    private final KinesisVideoMediaClient mediaClient;
    private final ImagePublisher imagePublisher;
    private final String streamARN;
    private StartSelector start;
    private final AtomicBoolean stop = new AtomicBoolean(false);

    public ProcessingTask(String streamARN, S3Client s3Client, KinesisVideoMediaClient mediaClient, String bucket, String directory, int imagePublisherThreads) {
        this.streamARN = streamARN;
        this.mediaClient = mediaClient;
        start = StartSelector.builder().startSelectorType(StartSelectorType.EARLIEST).build();

        imagePublisher = new ImagePublisher(s3Client, bucket, directory, imagePublisherThreads);
    }

    /**
     * Retrieves video stream data using {@link KinesisVideoMediaClient} and passes it to {@link MkvElementVisitor}
     * which in turn uses {@link FramePublishingDecoder} to process data, extract individual frames and store images in Amazon S3 bucket
     */
    @Override
    public void run() {
        LOG.info("Starting to wait for media data from stream: " + streamARN);
        do {
            GetMediaRequest request = GetMediaRequest.builder()
                    .streamARN(streamARN)
                    .startSelector(start)
                    .build();
            ResponseInputStream<GetMediaResponse> media = mediaClient.getMedia(request);
            try {
                if (media.response().sdkHttpResponse().isSuccessful()) {
                    Consumer<Optional<FragmentMetadata>> callback = fragmentMetadataOptional ->
                            fragmentMetadataOptional.ifPresent(fragmentMetadata -> {
                                        start = StartSelector.builder()
                                                .startSelectorType(StartSelectorType.FRAGMENT_NUMBER)
                                                .afterFragmentNumber(fragmentMetadata.getFragmentNumberString()).build();
                                    }
                            );

                    FrameVisitor.FrameProcessor frameProcessor = new FramePublishingDecoder(imagePublisher, callback);
                    MkvElementVisitor frameVisitor = FrameVisitor.create(frameProcessor, Optional.empty(), Optional.of(1L));
                    StreamingMkvReader.createDefault(new InputStreamParserByteSource(media)).apply(frameVisitor);
                } else {
                    LOG.debug("No media data in stream: "+streamARN);
                    Thread.sleep(200);
                }
            } catch (InterruptedException | MkvElementVisitException e) {
                LOG.error("Exception while processing stream: "+ streamARN, e);
                break;
            }
        } while (!stop.get());
        LOG.info("Finished processing stream: " + streamARN);
    }

    public void stop() {
        this.stop.set(true);
    }
}
