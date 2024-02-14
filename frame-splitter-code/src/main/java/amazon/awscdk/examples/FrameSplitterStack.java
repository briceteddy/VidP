package amazon.awscdk.examples;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.QueueProcessingFargateService;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;
import software.amazon.awscdk.services.sqs.Queue;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * CDK stack definition. It contains definitions of all AWS infrastructure resources except of Amazon Kinesis Video Stream used in this example.
 */
public class FrameSplitterStack extends Stack {
    public FrameSplitterStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public FrameSplitterStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        Vpc vpc = Vpc.Builder.create(this, "FrameSplitterVpc")
                .maxAzs(1)
                .build();

        Bucket outputBucket = Bucket.Builder.create(this, "s3OutputBucket")
                .accessControl(BucketAccessControl.PRIVATE)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .bucketName("frame-splitter-output-" + this.getAccount() + "-" + this.getRegion())
                .removalPolicy(RemovalPolicy.DESTROY)
                .publicReadAccess(false)
                .build();

        Queue queue = Queue.Builder.create(this, "TaskQueue")
                .queueName("FrameSplitterTaskQueue")
                .build();
        QueueProcessingFargateService fargateService = QueueProcessingFargateService.Builder.create(this, "QueueProcessingFargateService")
                .serviceName("QueueProcessingFargateService")
                .enableLogging(true)
                .image(ContainerImage.fromDockerImageAsset(DockerImageAsset.Builder.create(this, "dockerImageAsset").directory("docker").build()))
                .environment(Map.of(
                        "WAIT_SECONDS", "20",
                        "REGION", this.getRegion(),
                        "FRAME_SPLITTER_THREADS", "1",
                        "IMAGE_PUBLISHER_THREADS", "3",
                        "QUEUE_NAME", queue.getQueueName()
                )).queue(queue)
                .desiredTaskCount(1)
                .maxScalingCapacity(1)
                .memoryLimitMiB(4096)
                .cpu(512)
                .vpc(vpc)
                .build();

        fargateService.getTaskDefinition().addToTaskRolePolicy(PolicyStatement.Builder.create()
                .actions(asList(
                        "kinesisvideo:Get*",
                        "kinesisvideo:List*",
                        "kinesisvideo:Describe*"
                ))
                .effect(Effect.ALLOW)
                .resources(singletonList("arn:" + this.getPartition() + ":kinesisvideo:" + this.getRegion() + ":" + this.getAccount() + ":stream/*"))
                .build());

        fargateService.getTaskDefinition().addToTaskRolePolicy(PolicyStatement.Builder.create()
                .actions(singletonList("s3:PutObject"))
                .effect(Effect.ALLOW)
                .resources(singletonList(outputBucket.getBucketArn() + "/*"))
                .build());

        CfnOutput.Builder.create(this, "QueueUrl").exportName("QueueUrl").value(queue.getQueueUrl()).build();
        CfnOutput.Builder.create(this, "BucketName").exportName("BucketName").value(outputBucket.getBucketName()).build();
    }
}
