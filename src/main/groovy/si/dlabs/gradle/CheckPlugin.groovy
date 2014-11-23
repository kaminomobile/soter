package si.dlabs.gradle
import com.android.build.gradle.api.ApplicationVariant
import com.github.blazsolar.gradle.hipchat.tasks.SendMessageTask
import com.github.hipchat.api.messages.Message
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import si.dlabs.gradle.extensions.*
import si.dlabs.gradle.task.AfterAllTask
import si.dlabs.gradle.task.UploadTask
/**
 * Created by blazsolar on 02/09/14.z
 */
class CheckPlugin implements Plugin<Project> {

    def afterAll
    def doneTask

    @Override
    void apply(Project project) {

        if (!project.plugins.hasPlugin(com.android.build.gradle.AppPlugin)) {
            throw new RuntimeException("CheckPlugin requires android plugin")
        }

        CheckExtension baseExtension = project.extensions.create("check", CheckExtension)

        project.check.extensions.create("checkstyle", CheckstyleExtension)
        project.check.extensions.create("findbugs", FindbugsExtension)
        project.check.extensions.create("pmd", PMDExtension)
        project.check.extensions.create("logs", LogsExtension)
        project.check.extensions.create("tests", TestsExtension)

        project.check.extensions.create("publish", PublishExtension)
        project.check.publish.extensions.create("amazon", AmazonApkExtension)

        project.check.extensions.create("notifications", NotificationsExtension)
        project.check.notifications.extensions.create("hipchat", HipChatExtension)

        project.check.extensions.create("amazon", AmazonExtension)

        project.check.extensions.create("remote", RemoteExtension)

        project.apply plugin: "com.github.blazsolar.hipchat"

        project.configurations {
            rulesCheckstyle
            rulesFindbugs
            rulesPMD
        }

        afterAll = addAfterAll(project)
        doneTask = addDoneTask(project)

        project.afterEvaluate {
            addCheckstyleTask(project)
            addFindbugsTask(project)
            addPMDTask(project)
            addLogsTask(project)
            addTestsTasks(project)
            addPublishTasks(project)
            addNotificationsTasks(project)
            addRemotePushTask(project)
        }

    }

    private AfterAllTask addAfterAll(Project project) {

        def afterAll = project.tasks.create("afterAll", AfterAllTask)
        afterAll.setDescription("Waits that all jobs are executed")
        afterAll.setGroup("CI")

        def success = project.tasks.create("success") << {
            afterAll.thisSuccess = true
        }
        afterAll.mustRunAfter success

        def failed = project.tasks.create("failed") << {
            afterAll.thisSuccess = false
        }
        afterAll.mustRunAfter failed

        return afterAll

    }

    private Task addDoneTask(Project project) {

        Task done = project.tasks.create("ciDone")
        done.setDescription("Ci finished")
        done.setGroup("CI")
        done.onlyIf {
            return afterAll.isLead && afterAll.success
        }

        done.dependsOn afterAll
        return done

    }

    private Task addFailedTask(Project project) {

        Task failed = project.tasks.create("ciFailed")
        failed.setDescription("Ci failed")
        failed.setGroup("CI")
        failed.onlyIf {
            return afterAll.isLead && !afterAll.success
        }

        failed.dependsOn afterAll
        return failed

    }

    /**
     * Generates Checkstyle report for debug build.
     */
    private static void addCheckstyleTask(Project project) {

        if (project.check.checkstyle.enabled) {

            project.apply plugin: 'checkstyle'

            Task checkstyle = project.tasks.create("checkstyle", org.gradle.api.plugins.quality.Checkstyle);
            checkstyle.setDescription("Checkstyle for debug source")
            checkstyle.setGroup("Check")


            checkstyle.source project.android.sourceSets.main.java.getSrcDirs(), project.android.sourceSets.debug.java.getSrcDirs()
            checkstyle.include '**/*.java'
            checkstyle.exclude '**/gen/**'

            checkstyle.classpath = project.files()

            String outputDir = "$project.buildDir/outputs/reports/checkstyle"

            checkstyle.reports {
                xml {
                    destination outputDir + "/checkstyle.xml"
                }
            }

            checkstyle.configFile project.configurations.rulesCheckstyle.singleFile

            project.tasks.check.dependsOn checkstyle

            if (project.check.checkstyle.uploadReports && project.check.amazon.enabled) {

                Task upload = project.tasks.create("uploadCheckstyle", UploadTask);
                upload.setDescription("Upload checkstyle reports to amazon s3")
                upload.setGroup("Upload")

                upload.accessKey = project.check.amazon.accessKey
                upload.secretKey = project.check.amazon.secretKey

                upload.file = project.file(outputDir);
                upload.bucket project.check.amazon.bucket;
                upload.keyPrefix = project.check.amazon.path + "reports/"
                upload.isPublic = true

                checkstyle.finalizedBy upload

            }

        }

    }

    /**
     * Findbugs task fro debug source set
     */
    private static void addFindbugsTask(Project project) {

        if (project.check.findbugs.enabled) {

            project.apply plugin: 'findbugs'

            Task findbugs = project.tasks.create("findbugs", org.gradle.api.plugins.quality.FindBugs)
            findbugs.setDescription("Findbugs for debug source")
            findbugs.setGroup("Check")

            String outputDir = "$project.buildDir/outputs/reports/findbugs"

            findbugs.ignoreFailures = false
            findbugs.effort = project.check.findbugs.effort
            findbugs.reportLevel = project.check.findbugs.reportLevel
            findbugs.classes = project.files("$project.buildDir/intermediates/classes")

            findbugs.source project.android.sourceSets.main.java.getSrcDirs(), project.android.sourceSets.debug.java.getSrcDirs()
            findbugs.include '**/*.java'
            findbugs.exclude '**/gen/**'

            findbugs.reports {
                html {
                    enabled true
                    destination "$outputDir/findbugs.html"
                }
                xml {
                    enabled false
                    destination "$outputDir/findbugs.xml"
                    xml.withMessages true
                }
            }

            findbugs.classpath = project.files()

            findbugs.excludeFilter = project.configurations.rulesFindbugs.singleFile

            project.tasks.check.dependsOn findbugs
            findbugs.dependsOn "compileDebugJava"

            if (project.check.checkstyle.uploadReports && project.check.amazon.enabled) {

                Task upload = project.tasks.create("uploadFindbugs", UploadTask);
                upload.setDescription("Upload findbugs reports to amazon s3")
                upload.setGroup("Upload")

                upload.accessKey = project.check.amazon.accessKey
                upload.secretKey = project.check.amazon.secretKey

                upload.file = project.file(outputDir);
                upload.bucket project.check.amazon.bucket;
                upload.keyPrefix = project.check.amazon.path + "reports/"
                upload.isPublic = true

                findbugs.finalizedBy upload

            }

        }

    }

    private static void addPMDTask(Project project) {

        if (project.check.pmd.enabled) {

            project.apply plugin: 'pmd'

            Task pmd = project.tasks.create("pmd", org.gradle.api.plugins.quality.Pmd)
            pmd.setDescription("PMD for debug source")
            pmd.setGroup("Check")

            pmd.ignoreFailures = false
            pmd.ruleSets = ["java-basic", "java-braces", "java-strings", "java-android"]

            pmd.source project.android.sourceSets.main.java.getSrcDirs(), project.android.sourceSets.debug.java.getSrcDirs()
            pmd.include '**/*.java'
            pmd.exclude '**/gen/**'

            String outputDir = "$project.buildDir/outputs/reports/pmd"

            pmd.reports {
                html {
                    enabled = true
                    destination "$outputDir/pmd.html"
                }
                xml {
                    enabled = true
                    destination "$outputDir/pmd.xml"
                }
            }

            pmd.ruleSetFiles = project.files(project.configurations.rulesPMD.files)

            project.tasks.check.dependsOn pmd

            if (project.check.checkstyle.uploadReports && project.check.amazon.enabled) {

                Task upload = project.tasks.create("uploadPmd", UploadTask);
                upload.setDescription("Upload pmd reports to amazon s3")
                upload.setGroup("Upload")

                upload.accessKey = project.check.amazon.accessKey
                upload.secretKey = project.check.amazon.secretKey

                upload.file = project.file(outputDir);
                upload.bucket project.check.amazon.bucket;
                upload.keyPrefix = project.check.amazon.path + "reports/"
                upload.isPublic = true

                pmd.finalizedBy upload

            }
        }

    }

    private static void addLogsTask(Project project) {

        if (project.check.logs.uploadReports && project.check.amazon.enabled) {

            String outputDir = "$project.buildDir/outputs/reports/logs"

            Exec logs = project.tasks.create("logs", Exec);
            logs.outputs.file project.file(outputDir + "/emulator.log")
            logs.commandLine = ["adb", "logcat", "-d"]
            logs.doFirst {
                File file = new File(outputDir);
                file.mkdirs();

                standardOutput = new FileOutputStream(new File(file, "emulator.log"))
            }

            Task upload = project.tasks.create("uploadLogs", UploadTask);
            upload.setDescription("Upload logs to amazon s3")
            upload.setGroup("Upload")

            upload.accessKey = project.check.amazon.accessKey;
            upload.secretKey = project.check.amazon.secretKey;

            upload.file = project.file(outputDir)
            upload.bucket = project.check.amazon.bucket;
            upload.keyPrefix = project.check.amazon.path + "reports/"
            upload.isPublic = true

            logs.finalizedBy upload
            project.tasks.connectedAndroidTest.finalizedBy logs

        }

    }

    private static void addTestsTasks(Project project) {

        if (project.check.tests.uploadReports && project.check.amazon.enabled) {

            UploadTask upload = project.tasks.create("uploadTestReports", UploadTask)
            upload.setDescription("Upload test reports to amazon s3")
            upload.setGroup("Upload")

            upload.accessKey = project.check.amazon.accessKey
            upload.secretKey = project.check.amazon.secretKey

            upload.file = project.file("$project.buildDir/outputs/reports/androidTests")
            upload.bucket = project.check.amazon.bucket
            upload.keyPrefix = project.check.amazon.path + "reports/"
            upload.isPublic = true;

            project.tasks.connectedAndroidTest.finalizedBy upload

        }

    }

    private void addPublishTasks(Project project) {

        if (project.check.publish.enabled) {

            addApkTask(project)

        }

    }

    private void addApkTask(Project project) {

        String uploadTaskName = "uploadApk" + project.check.publish.amazon.variant.capitalize();

        Task upload = project.tasks.create("uploadApk")
        upload.setDescription("Upload apk to amazon s3")
        upload.setGroup("Upload")
        upload.onlyIf {
            return afterAll.isLead && !afterAll.success
        }

        doneTask.dependsOn upload

        if (project.check.publish.amazon.upload && project.check.amazon.enabled) {

            UploadTask uploadVariant = project.tasks.create(uploadTaskName, UploadTask)
            uploadVariant.setDescription("Upload apk variant to amazon s3")
            uploadVariant.setGroup("Upload")

            uploadVariant.accessKey = project.check.amazon.accessKey
            uploadVariant.secretKey = project.check.amazon.secretKey

            uploadVariant.bucket = project.check.amazon.bucket
            uploadVariant.keyPrefix = project.check.amazon.path + "binary/"
            uploadVariant.isPublic = false

            project.android.applicationVariants.all { ApplicationVariant variant ->
                if (variant.getName().equals(project.check.publish.amazon.variant)) {

                    def files = new File[variant.getOutputs().size()];

                    variant.getOutputs().eachWithIndex { output, index ->
                        files[index] = output.getOutputFile()
                    }

                    uploadVariant.files = files;
                    uploadVariant.dependsOn variant.getAssemble()
                    upload.dependsOn uploadVariant
                }
            }

        }

    }

    private void addNotificationsTasks(Project project) {

        if (project.check.notifications.enabled) {

            addHipChatTask(project)

        }

    }

    private void addHipChatTask(Project project) {

        if (project.check.notifications.hipchat.enabled) {

            project.hipchat.token = project.check.notifications.hipchat.token

            String userName = "Travis CI"
            String textPrefix = System.getenv("TRAVIS_REPO_SLUG") + "#" + System.getenv("TRAVIS_BUILD_ID") + " (" + System.getenv("TRAVIS_BRANCH") + " - " + System.getenv("TRAVIS_COMMIT").substring(0, 6) + "): "

            SendMessageTask passed = project.tasks.create("notifyHipChatPassed", SendMessageTask)
            passed.roomId = project.check.notifications.hipchat.roomId
            passed.userId = project.check.notifications.hipchat.userId
            passed.userName = userName
            passed.color = Message.Color.GREEN
            passed.message = textPrefix + "the build has passed"
            passed.onlyIf {
                return afterAll.isLead && !afterAll.success
            }
            doneTask.dependsOn passed

            SendMessageTask failed = project.tasks.create("notifyHipChatFailed", SendMessageTask)
            failed.roomId = project.check.notifications.hipchat.roomId
            failed.userId = project.check.notifications.hipchat.userId
            failed.userName = userName
            failed.color = Message.Color.RED
            failed.message = textPrefix + "the build has failed"
            failed.onlyIf {
                return afterAll.isLead && afterAll.success
            }
            doneTask.dependsOn failed

        }

    }

    private void addRemotePushTask(Project project) {

        if (project.check.remote.pushToRemote) {

            // TODO use grgit, waiting for version 0.4.0

            Exec remoteAdd = project.tasks.create("addRemote", Exec)
            remoteAdd.commandLine = ["git", "remote", "add", "check-plugin-remote", project.check.remote.remote]

            def remote = project.tasks.create("pushRemoteTask", Exec)
            remote.setDescription("Push repo to remote")
            remote.setGroup("Git")
            remote.commandLine = ["git", "push", "check-plugin-remote", project.check.remote.branch, "--tags"]
            remote.dependsOn remoteAdd
            remote.onlyIf {
                return afterAll.isLead && !afterAll.success
            }

            doneTask.dependsOn remote

        }

    }

}
