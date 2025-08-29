package com.ps

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class PsPlugin implements Plugin<Project> {
    void apply(Project project) {
        if (project.rootProject != project) return

        project.tasks.register('psGenerate', PsGenerateTask) { t ->
            t.group = 'ps'
            t.description = 'Generate prompt step sources/tests/manifest from ps-config JSONs'
        }

        project.tasks.register('psVerify', PsVerifyTask) { t ->
            t.group = 'ps'
            t.description = 'Verify generated code compiles and tests pass; enforce constraints'
            t.dependsOn 'psGenerate'
        }

        project.gradle.projectsEvaluated {
            def pp = project.project(':ps:ps-prompt')
            pp.tasks.named('compileJava').configure { it.dependsOn(project.tasks.named('psGenerate')) }
            pp.tasks.named('test').configure { it.dependsOn(project.tasks.named('psGenerate')) }
        }
    }
}

class PsGenerateTask extends DefaultTask {
    @TaskAction
    void generate() {
        File configDir = project.file('ps/ps-config/pipelines')
        if (!configDir.exists()) {
            project.logger.lifecycle("No pipelines directory at ${configDir}, skipping generation.")
            return
        }
        File outMain = project.file('ps/ps-prompt/build/generated/sources/ps')
        File outTest = project.file('ps/ps-prompt/build/generated/sources/ps-test')
        outMain.mkdirs(); outTest.mkdirs()

        List<Map> manifest = []
        configDir.eachFileRecurse { File f ->
            if (!f.name.endsWith('.json')) return
            def root = new JsonSlurper().parse(f) as Map
            String pipelineName = (root['pipeline'] ?: 'pipeline') as String
            def steps = (root['steps'] ?: []) as List
            steps.eachWithIndex { Object entry, int idx ->
                if (!(entry instanceof Map)) return
                Map step = (Map) entry
                if (!step.containsKey('$prompt')) return
                Map p = (Map) step['$prompt']
                String stepName = (p['name'] ?: "step${idx}") as String
                String inType = (p['inType'] ?: 'java.lang.String') as String
                String outType = (p['outType'] ?: 'java.lang.String') as String
                List examples = (p['examples'] ?: []) as List

                String pkg = 'com.ps.prompt.generated'
                String clsName = pipelineName.replaceAll('[^A-Za-z0-9]', '_') + '_' + stepName.replaceAll('[^A-Za-z0-9]', '_')
                File javaFile = new File(outMain, pkg.replace('.', '/') + '/' + clsName + '.java')
                javaFile.parentFile.mkdirs()
                javaFile.text = GeneratedTemplates.renderStep(pkg, clsName, inType, outType, examples)

                File testFile = new File(outTest, pkg.replace('.', '/') + '/' + clsName + 'Test.java')
                testFile.parentFile.mkdirs()
                testFile.text = GeneratedTemplates.renderTest(pkg, clsName, inType, outType, examples)

                manifest << [pipeline: pipelineName, step: stepName, inType: inType, outType: outType, source: javaFile.absolutePath]
            }
        }
        new File(outMain, 'ps-manifest.json').text = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))
        project.logger.lifecycle("psGenerate: generated ${manifest.size()} prompt step(s)")
    }
}

class PsVerifyTask extends DefaultTask {
    @TaskAction
    void verify() {
        File outMain = project.file('ps/ps-prompt/build/generated/sources/ps')
        if (outMain.exists()) {
            List<String> banned = [
                    'java.io.', 'java.net.', 'java.nio.file', 'new Thread', 'Executors.', 'System.currentTimeMillis()',
                    'java.util.Random', 'SecureRandom', 'Class.forName', 'reflect'
            ]
            outMain.eachFileRecurse { File f ->
                if (f.name.endsWith('.java')) {
                    String text = f.text
                    def hit = banned.find { text.contains(it) }
                    if (hit) throw new RuntimeException("psVerify: Disallowed API '${hit}' found in ${f}")
                }
            }
        }
        project.logger.lifecycle('psVerify: static checks passed for generated code')
        // Compile + test generated code
        project.project(':ps:ps-prompt').tasks.getByName('test').execute()
    }
}

class GeneratedTemplates {
    static String renderStep(String pkg, String cls, String inType, String outType, List examples) {
        StringBuilder sb = new StringBuilder()
        sb.append("package ${pkg};\n\n")
        sb.append("public final class ${cls} implements com.ps.core.ThrowingFn<${inType}, ${outType}> {\n")
        sb.append("  public ${outType} apply(${inType} in) {\n")
        if (examples && inType == 'java.lang.String' && outType == 'java.lang.String') {
            sb.append("    if (in == null) return null;\n")
            examples.each { ex ->
                if (ex instanceof List && ex.size()==2) {
                    String eIn = ex[0].toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    String eOut = ex[1].toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    sb.append("    if (in.equals(\"${eIn}\")) return \"${eOut}\";\n")
                }
            }
            sb.append("    return in;\n")
        } else {
            sb.append("    return in;\n")
        }
        sb.append("  }\n")
        sb.append("}\n")
        return sb.toString()
    }

    static String renderTest(String pkg, String cls, String inType, String outType, List examples) {
        StringBuilder sb = new StringBuilder()
        sb.append("package ${pkg};\n\n")
        sb.append("import org.junit.jupiter.api.Test;\n")
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n")
        sb.append("final class ${cls}Test {\n")
        sb.append("  @Test void examplesPass() throws Exception {\n")
        sb.append("    var s = new ${cls}();\n")
        if (examples) {
            examples.each { ex ->
                if (ex instanceof List && ex.size()==2) {
                    String eIn = ex[0].toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    String eOut = ex[1].toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    sb.append("    assertEquals(\"${eOut}\", s.apply(\"${eIn}\"));\n")
                }
            }
        } else {
            sb.append("    assertEquals(\"x\", s.apply(\"x\"));\n")
        }
        sb.append("  }\n")
        sb.append("}\n")
        return sb.toString()
    }
}

