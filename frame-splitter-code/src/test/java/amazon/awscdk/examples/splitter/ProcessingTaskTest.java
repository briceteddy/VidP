package amazon.awscdk.examples.splitter;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.kinesisvideo.parser.examples.KinesisVideoExample;
import com.amazonaws.regions.Regions;
import org.apache.commons.lang3.Validate;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.kinesisvideomedia.KinesisVideoMediaClient;
import software.amazon.awssdk.services.kinesisvideomedia.model.GetMediaRequest;
import software.amazon.awssdk.services.kinesisvideomedia.model.GetMediaResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ProcessingTaskTest {

    @Test
    public void splitterTest(@TempDir Path tempDir) {
        S3Client s3Client = mock(S3Client.class);
        System.out.println(tempDir.toString());

        doAnswer(invocationOnMock -> {
            PutObjectRequest request = invocationOnMock.getArgument(0, PutObjectRequest.class);
            RequestBody body = invocationOnMock.getArgument(1, RequestBody.class);

            Path imagePath = tempDir.resolve(request.key());
            Files.createDirectories(imagePath.getParent());
            Files.write(imagePath, body.contentStreamProvider().newStream().readAllBytes(), StandardOpenOption.CREATE_NEW);

            return PutObjectResponse.builder().build();
        }).when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        InputStream testInputStream = getTestInputStream("vogels_330.mkv");
        KinesisVideoMediaClient mediaClient = mock(KinesisVideoMediaClient.class);
        doAnswer(invocationOnMock -> {
            SdkHttpResponse sdkHttpResponse = SdkHttpResponse.builder().statusCode(200).build();
            GetMediaResponse.Builder responseBuilder = GetMediaResponse.builder();
            responseBuilder.applyMutation(builder -> builder.sdkHttpResponse(sdkHttpResponse));

            return new ResponseInputStream<>(responseBuilder.build(), AbortableInputStream.create(testInputStream));
        }).when(mediaClient).getMedia(any(GetMediaRequest.class));

        ProcessingTask processingTask = new ProcessingTask("testStreamARN", s3Client, mediaClient, "bucket", "images", 5);
        processingTask.stop();
        processingTask.run();
    }

    @Ignore
    @Test
    public void testExample() throws InterruptedException, IOException {
        /**
         Replace AWS_PROFILE_NAME with the name of AWS credentials profile corresponding to the account and user used to create video stream
         */
        String profileName = "AWS_PROFILE_NAME";
        String streamName = "frame-splitter-test";
        String uploadVideofileName = "vogels_330.mkv";
        Regions region = Regions.EU_CENTRAL_1;

        KinesisVideoExample example = KinesisVideoExample.builder().region(region)
                .streamName(streamName)
                .credentialsProvider(new ProfileCredentialsProvider(profileName))
                .inputVideoStream(getTestInputStream(uploadVideofileName))
                .build();

        example.execute();
    }

    public static InputStream getTestInputStream(String name) {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(name);
        Validate.isTrue(inputStream != null, "Could not read input file " + name);
        return inputStream;
    }
}
