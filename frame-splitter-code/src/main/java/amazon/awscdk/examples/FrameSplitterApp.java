package amazon.awscdk.examples;

import software.amazon.awscdk.core.App;

/**
 * Main class for CDK application
 */
public class FrameSplitterApp {
    public static void main(final String[] args) {
        App app = new App();

        new FrameSplitterStack(app, "FrameSplitterStack");

        app.synth();
    }
}
