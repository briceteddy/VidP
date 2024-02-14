package amazon.awscdk.examples.splitter;

public class ProcessingRequest {
    private String streamARN;
    private String bucket;
    private String s3Directory;

    public String getStreamARN() {
        return streamARN;
    }

    public void setStreamARN(String streamARN) {
        this.streamARN = streamARN;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getS3Directory() {
        return s3Directory;
    }

    public void setS3Directory(String s3Directory) {
        this.s3Directory = s3Directory;
    }
}
