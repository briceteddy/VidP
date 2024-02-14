FROM amazonlinux:2
RUN yum update -y
RUN yum install awslogs -y
RUN yum install amazon-cloudwatch-agent -y
RUN yum install java-11-amazon-corretto-headless -y

COPY frame-splitter-0.1-SNAPSHOT-shaded.jar /opt/

WORKDIR /opt/
ENTRYPOINT java -cp frame-splitter-0.1-SNAPSHOT-shaded.jar amazon.awscdk.examples.splitter.FrameSplitter
