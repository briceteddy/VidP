package amazon.awscdk.examples.splitter;

import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class used to publish images to S3 bucket.
 */
public class ImagePublisher {
    private static final Logger LOG = LoggerFactory.getLogger(ImagePublisher.class);
    private final S3Client s3Client;
    private final String bucket;
    private String directory;
    private final ExecutorService executorService;
    private BigInteger counter = BigInteger.ONE;

    public ImagePublisher(S3Client s3Client, String bucket, String directory, int threadsNumber) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.directory = directory;
        executorService = Executors.newFixedThreadPool(threadsNumber);
    }

    /**
     * Converts received image bytes to image file and stores it in S3 bucket
     * @param bufferedImage - image bytes
     */
    public void publish(BufferedImage bufferedImage) {
        LOG.info("Publishing image no.: " + counter.toString());
        directory = directory.endsWith("/") ? directory : directory + "/";
        String key = directory +counter.toString();
        executorService.submit(new InternalTask(bufferedImage, s3Client, bucket, key, "png"));
        counter = counter.add(BigInteger.ONE);
    }

    private static class InternalTask implements Callable<Boolean> {
        private static final Logger LOG = LoggerFactory.getLogger(InternalTask.class);
        private final BufferedImage bufferedImage;
        private final S3Client s3Client;
        private final String bucket;
        private final String key;
        private final String extension;

        InternalTask(BufferedImage bufferedImage, S3Client s3Client, String bucket, String key, String extension) {
            this.bufferedImage = bufferedImage;
            this.s3Client = s3Client;
            this.bucket = bucket;
            this.key = key;
            this.extension = extension;
        }

        /**
         * Converts received image bytes to image file and stores it in S3 bucket
         * @return
         * @throws IOException
         */
        @Override
        public Boolean call() throws IOException {
            LOG.info("Start internal image publish: " + bucket + key + "." + extension);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(bufferedImage, extension, baos);
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket).key(key + "." + extension)
                        .build();
                RequestBody requestBody = RequestBody.fromBytes(baos.toByteArray());
                s3Client.putObject(putObjectRequest, requestBody);
            } catch (IOException e) {
                LOG.error("Failed to write frame to S3", e);
                throw new IOException("Failed to write frame to S3", e);
            }
            LOG.info("Successfully published image: " + bucket + key + "." + extension);
            return true;
        }
    }
}
