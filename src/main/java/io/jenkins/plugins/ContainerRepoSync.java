package io.jenkins.plugins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class ContainerRepoSync extends Builder implements SimpleBuildStep {

    private final String sourceRepo;
    private final String targetRepo;
    private final String sourceTags;

    @DataBoundConstructor
    public ContainerRepoSync(String sourceRepo, String targetRepo, String sourceTags) {
        this.sourceRepo = sourceRepo;
        this.targetRepo = targetRepo;
        this.sourceTags = sourceTags;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public String getTargetRepo() {
        return targetRepo;
    }

    public String getsourceTags() {
        return sourceTags;
    }

    // Download all tags by running docker pull command
    private void downloadImages(List<String> tags, TaskListener listener) throws IOException, InterruptedException {
        for (String tag : tags) {
            List<String> commands = new ArrayList<String>();
            commands.add("docker");
            commands.add("pull");
            commands.add(sourceRepo + ":" + tag);

            listener.getLogger().println(commands);

            ProcessBuilder pb = new ProcessBuilder(commands);
            Process process = pb.start();

            StringBuilder out = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null, previous = null;
            while ((line = br.readLine()) != null)
                if (!line.equals(previous)) {
                    previous = line;
                    out.append(line).append('\n');
                }

            //Check result
            if (process.waitFor() == 0) {
                listener.getLogger().println("Success!");
                listener.getLogger().println(out.toString());
            } else {
                listener.getLogger().println("Failure!");
                throw new IOException(out.toString());
            }
        }
    }

    // Push all tags by running docker push command
    private void uploadImages(List<String> tags, TaskListener listener) throws IOException, InterruptedException {
        for (String tag : tags) {
            List<String> tagCommands = new ArrayList<String>();
            tagCommands.add("docker");
            tagCommands.add("tag");
            tagCommands.add(sourceRepo + ":" + tag);
            tagCommands.add(targetRepo + ":" + tag);

            ProcessBuilder pb = new ProcessBuilder(tagCommands);
            Process process = pb.start();

            StringBuilder out = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null, previous = null;
            while ((line = br.readLine()) != null)
                if (!line.equals(previous)) {
                    previous = line;
                    out.append(line).append('\n');
                }

            process.waitFor();

            List<String> commands = new ArrayList<String>();
            commands.add("docker");
            commands.add("push");
            commands.add(targetRepo + ":" + tag);
            
            listener.getLogger().println(commands);

            pb = new ProcessBuilder(commands);
            process = pb.start();

            out = new StringBuilder();
            br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            line = null;
            previous = null;
            while ((line = br.readLine()) != null)
                if (!line.equals(previous)) {
                    previous = line;
                    out.append(line).append('\n');
                }

            //Check result
            if (process.waitFor() == 0) {
                listener.getLogger().println("Success!");
                listener.getLogger().println(out.toString());
            } else {
                listener.getLogger().println("Failure!");
                throw new IOException(out.toString());
            }
        }
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        List<String> tags = new ArrayList<String>();

        // if empty source tag, add latest; else split string with , and add all
        if (getsourceTags().length() == 0) {
            tags.add("latest");
        } else {
            String[] parts = getsourceTags().split(",");
            for (String part : parts) {
                if (part.length() > 0) {
                    tags.add(part);
                }
            }
        }

        listener.getLogger().println("Syncing tags=" + tags + " from " + sourceRepo + " to " + targetRepo);
        downloadImages(tags, listener);
        uploadImages(tags, listener);
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckSourceRepo(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.ContainerRepoSync_DescriptorImpl_errors_emptySourceName());
    
            return FormValidation.ok();
        }

        public FormValidation doCheckTargetRepo(@QueryParameter String value)
                throws IOException, ServletException {

            if (value.length() == 0)
                return FormValidation.error(Messages.ContainerRepoSync_DescriptorImpl_errors_emptyTargetName());

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Container Repo Sync";
        }

    }
    
}
