package amazon.awscdk.examples.splitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesisvideo.KinesisVideoClient;
import software.amazon.awssdk.services.kinesisvideo.model.APIName;
import software.amazon.awssdk.services.kinesisvideo.model.GetDataEndpointRequest;
import software.amazon.awssdk.services.kinesisvideo.model.GetDataEndpointResponse;
import software.amazon.awssdk.services.kinesisvideomedia.KinesisVideoMediaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main class for the application. Its main purpose is to listen for incoming Amazon SQS messages and triggering processing.
 */
public class FrameSplitter {
    private static final Logger LOG = LoggerFactory.getLogger(FrameSplitter.class);
    /**
     * Environment variable name. Comes from the {@link software.amazon.awscdk.services.ecs.patterns.QueueProcessingFargateService} definition.
     * Read more:
     *
     * @see <a href="https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_aws-ecs-patterns.QueueProcessingFargateService.html#environment">QueueProcessingFargateService#environment</a>
     */
    private static final String QUEUE_NAME = "QUEUE_NAME";
    private static final String WAIT_SECONDS = "WAIT_SECONDS";
    private static final String REGION = "REGION";
    private static final String FRAME_SPLITTER_THREADS = "FRAME_SPLITTER_THREADS";
    private static final String IMAGE_PUBLISHER_THREADS = "IMAGE_PUBLISHER_THREADS";

    private static final String WAIT_SECONDS_DEFAULT = "20";
    private static final String IMAGE_PUBLISHER_THREADS_DEFAULT = "1";
    private static final String FRAME_SPLITTER_THREADS_DEFAULT = "1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AwsCredentialsProvider credentialsProvider;
    private final ExecutorService executorService;
    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final String queueUrl;
    private final int waitSeconds;
    private final int imagePublisherThreads;
    private final Region region;

    public FrameSplitter(Region region, AwsCredentialsProvider credentialsProvider, SqsClient sqsClient, S3Client s3Client, String queueUrl, int waitSeconds, int frameSplitterThreads, int imagePublisherThreads) {
        executorService = Executors.newFixedThreadPool(frameSplitterThreads);
        this.region = region;
        this.credentialsProvider = credentialsProvider;
        this.sqsClient = sqsClient;
        this.s3Client = s3Client;
        this.waitSeconds = waitSeconds;
        this.queueUrl = queueUrl;
        this.imagePublisherThreads = imagePublisherThreads;
    }

    private void start() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .visibilityTimeout(10)
                .waitTimeSeconds(waitSeconds)
                .build();

        while (true) {
            ReceiveMessageResponse response = sqsClient.receiveMessage(request);
            List<Message> messages = response.messages();
            if (messages.isEmpty()) {
                LOG.info("No messages");
            }
            for (Message message : messages) {
                LOG.info("Received message [" + message.messageId() + "] from SQS: " + message.body());
                try {
                    ProcessingRequest processingRequest = objectMapper.readValue(message.body(), ProcessingRequest.class);

                    KinesisVideoClient client = KinesisVideoClient.builder()
                            .credentialsProvider(credentialsProvider).region(region).build();

                    GetDataEndpointRequest getDataEndpointRequest = GetDataEndpointRequest.builder()
                            .apiName(APIName.GET_MEDIA)
                            .streamARN(processingRequest.getStreamARN()).build();
                    GetDataEndpointResponse dataEndpoint = client.getDataEndpoint(getDataEndpointRequest);

                    KinesisVideoMediaClient mediaClient = KinesisVideoMediaClient.builder()
                            .endpointOverride(URI.create(dataEndpoint.dataEndpoint()))
                            .credentialsProvider(credentialsProvider).region(region).build();

                    ProcessingTask task = new ProcessingTask(processingRequest.getStreamARN(), s3Client, mediaClient,
                            processingRequest.getBucket(), processingRequest.getS3Directory(), imagePublisherThreads);
                    executorService.submit(task);
                    LOG.info("Message [" + message.messageId() + "] submitter for processing");
                } catch (JsonProcessingException e) {
                    LOG.error("Can't deserialize message body: " + message.body(), e);
                    //We could send it to dead letter queue or raise and alert
                } finally {
                    sqsClient.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                }
            }
        }
    }

    public static void main(String[] args) throws ParseException {
        LOG.info("Starting Frame Splitter");

        CommandLineParser parser = new DefaultParser();
        Option queueNameOption = Option.builder("q").required(false).longOpt("queue").hasArg().type(String.class)
                .desc("SQS queue name").build();
        Option frameSplitterThreadsOption = Option.builder("ft").required(false).longOpt("frame-threads").hasArg().type(Integer.class)
                .desc("Number of vidoes stream processing threads").build();
        Option imagePublsherThreadsOption = Option.builder("pt").required(false).longOpt("publisher-threads").hasArg().type(Integer.class)
                .desc("Number of image publisher threads").build();
        Option profileOption = Option.builder("p").required(false).longOpt("profile").hasArg().type(String.class)
                .desc("AWS credentials profile").build();
        Option regionOption = Option.builder("r").required(false).longOpt("region").hasArg().type(String.class)
                .desc("AWS region where SQS is located").build();
        Option waitSecondsOption = Option.builder("w").required(false).longOpt("wait").hasArg().type(Integer.class)
                .desc("Number of seconds to wait for SQS message on single loop. Must be >= 0 and <= 20").build();

        Options options = new Options()
                .addOption(queueNameOption)
                .addOption(frameSplitterThreadsOption)
                .addOption(imagePublsherThreadsOption)
                .addOption(profileOption)
                .addOption(regionOption)
                .addOption(waitSecondsOption);
        CommandLine commandLine = parser.parse(options, args);

        String profile = commandLine.getOptionValue(profileOption.getOpt());
        String regionStr = commandLine.getOptionValue(regionOption.getOpt(), System.getenv(REGION));
        String queueName = commandLine.getOptionValue(queueNameOption.getOpt(), System.getenv(QUEUE_NAME));
        int waitSeconds = Integer.parseInt(commandLine.getOptionValue(waitSecondsOption.getOpt(), Optional.ofNullable(System.getenv(WAIT_SECONDS)).orElse(WAIT_SECONDS_DEFAULT)));
        if (waitSeconds < 0 || waitSeconds > 20) {
            throw new IllegalArgumentException("waitSeconds must be >= 0 and <= 20");
        }
        int frameSplitterThreads = Integer.parseInt(commandLine.getOptionValue(frameSplitterThreadsOption.getOpt(), Optional.ofNullable(System.getenv(FRAME_SPLITTER_THREADS)).orElse(FRAME_SPLITTER_THREADS_DEFAULT)));
        int imagePublisherThreads = Integer.parseInt(commandLine.getOptionValue(imagePublsherThreadsOption.getOpt(), Optional.ofNullable(System.getenv(IMAGE_PUBLISHER_THREADS)).orElse(IMAGE_PUBLISHER_THREADS_DEFAULT)));

        if (StringUtils.isBlank(regionStr) || StringUtils.isBlank(queueName)) {
            throw new IllegalArgumentException("Region and queue name is required");
        }

        AwsCredentialsProvider credentialsProvider = profile != null ? ProfileCredentialsProvider.create(profile) : DefaultCredentialsProvider.create();
        Region region = Region.of(regionStr);

        SqsClient sqsClient = SqsClient.builder().credentialsProvider(credentialsProvider).region(region).build();
        GetQueueUrlResponse queueUrlResponse = sqsClient.getQueueUrl(builder -> builder.queueName(queueName));

        S3Client s3Client = S3Client.builder().credentialsProvider(credentialsProvider).region(region).build();

        String paramsStr = StringUtils.joinWith(" ",
                "profile: ", profile, "|",
                "region:", region, "|",
                "queueName:", queueName, "|",
                "queueUrl:", queueUrlResponse.queueUrl(), "|",
                "waitSeconds:", waitSeconds, "|",
                "frameSplitterThreads:", frameSplitterThreads, "|",
                "imagePublisherThreads:", imagePublisherThreads);
        LOG.info("Running with params: " + paramsStr);
        new FrameSplitter(region, credentialsProvider, sqsClient, s3Client, queueUrlResponse.queueUrl(), waitSeconds, frameSplitterThreads, imagePublisherThreads).start();
    }

}
