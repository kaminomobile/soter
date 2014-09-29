package si.dlabs.gradle

import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import si.dlabs.gradle.extensions.AmazonExtension
import si.dlabs.gradle.extensions.ApkExtension
import si.dlabs.gradle.extensions.CheckExtension
import si.dlabs.gradle.extensions.CheckstyleExtension
import si.dlabs.gradle.extensions.FindbugsExtension
import si.dlabs.gradle.extensions.LogsExtension
import si.dlabs.gradle.extensions.PMDExtension
import si.dlabs.gradle.extensions.TestsExtension
import si.dlabs.gradle.task.UploadTask

/**
 * Created by blazsolar on 02/09/14.
 */
class CheckPlugin implements Plugin<Project> {

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
        project.check.extensions.create("amazon", AmazonExtension)
        project.check.extensions.create("apk", ApkExtension)

        project.configurations {
            rulesCheckstyle
            rulesFindbugs
            rulesPMD
        }

        project.afterEvaluate {
            addCheckstyleTask(project)
            addFindbugsTask(project)
            addPMDTask(project)
            addLogsTask(project)
            addTestsTasks(project)
            addApkTask(project)
        }

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

            String output = "$project.buildDir/outputs/reports/checkstyle/checkstyle.xml"

            checkstyle.reports {
                xml {
                    destination output
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

                upload.file = project.file(output);
                upload.bucket project.check.amazon.bucket;
                upload.keyPrefix = project.check.amazon.path + "reports/"

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
            findbugs.effort = "max"
            findbugs.reportLevel = "low"
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
            pmd.ruleSets = ["basic", "braces", "strings", "android"]

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

            project.tasks.connectedAndroidTest.finalizedBy upload

        }

    }

    private static void addApkTask(Project project) {

        String uploadTaskName = "uploadApk" + project.check.apk.variant.capitalize();

        Task upload = project.tasks.create("uploadApk")
        upload.setDescription("Upload apk to amazon s3")
        upload.setGroup("Upload")

        if (project.check.apk.upload && project.check.amazon.enabled) {

            UploadTask uploadVariant = project.tasks.create(uploadTaskName, UploadTask)
            uploadVariant.setDescription("Upload apk variant to amazon s3")
            uploadVariant.setGroup("Upload")

            uploadVariant.accessKey = project.check.amazon.accessKey
            uploadVariant.secretKey = project.check.amazon.secretKey

            uploadVariant.bucket = project.check.amazon.bucket
            uploadVariant.keyPrefix = project.check.amazon.path + "binary/"

            project.android.applicationVariants.all { ApplicationVariant variant ->
                if (variant.getName().equals(project.check.apk.variant)) {

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

}
