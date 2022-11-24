package au.com.dius.pact.provider.gradle

import groovy.transform.CompileStatic
import org.gradle.api.GradleScriptException
import org.gradle.api.Project

/**
 * Main plugin class
 */
class PactPlugin extends PactPluginBase {

  @Override
    void apply(Project project) {

        // Create and install the extension object
        project.extensions.create('pact', PactPluginExtension, project.container(GradleProviderInfo,
          new ProviderInfoFactory(project)))

        project.task(PACT_VERIFY, description: 'Verify your pacts against your providers', group: GROUP)
        project.task('pactPublish', description: 'Publish your pacts to a pact broker', type: PactPublishTask,
            group: GROUP)
        project.task('canIDeploy', description: 'Check if it is safe to deploy by checking whether or not the ' +
          'specified pacticipant versions are compatible', type: PactCanIDeployTask,
          group: GROUP)

        project.afterEvaluate {
          if (it.pact == null) {
            throw new GradleScriptException('No pact block was found in the project', null)
          } else if (!(it.pact instanceof PactPluginExtension)) {
            throw new GradleScriptException('Your project is misconfigured, was expecting a \'pact\' configuration ' +
              "in the build, but got a ${it.pact.class.simpleName} with value '${it.pact}' instead. " +
              'Make sure there is no property that is overriding \'pact\'.', null)
          }

          it.pact.serviceProviders.all { GradleProviderInfo provider ->
            setupPactConsumersFromBroker(provider, project, it.pact)

                def taskName = {
                  def defaultName = "pactVerify_${provider.name.replaceAll(/\s+/, '_')}".toString()
                  try {
                    def clazz = this.getClass().classLoader.loadClass('org.gradle.util.NameValidator').metaClass
                    def asValidName = clazz.getMetaMethod('asValidName', [String])
                    if (asValidName) {
                      return asValidName.invoke(clazz.newInstance(), [ defaultName ])
                    }
                    // Gradle versions > 4.6 no longer have an instance method
                    return defaultName
                  } catch (ClassNotFoundException e) {
                    // Earlier versions of Gradle don't have NameValidator
                    // Without it, we just don't change the task name
                    return defaultName
                  } catch (NoSuchMethodException e) {
                    // Gradle versions > 4.6 no longer have an instance method
                    return defaultName
                  }
                } ()

                def providerTask = project.task(taskName,
                    description: "Verify the pacts against ${provider.name}", type: PactVerificationTask,
                    group: GROUP) {
                    providerToVerify = provider
                }

                if (project.tasks.findByName(TEST_CLASSES)) {
                  providerTask.dependsOn TEST_CLASSES
                }

                if (provider.startProviderTask != null) {
                    providerTask.dependsOn(provider.startProviderTask)
                }

                if (provider.terminateProviderTask != null) {
                    providerTask.finalizedBy(provider.terminateProviderTask)
                }

                if (provider.dependencyForPactVerify) {
                    it.pactVerify.dependsOn(providerTask)
                }
            }
        }
    }

  @SuppressWarnings('CatchRuntimeException')
  @CompileStatic
  private void setupPactConsumersFromBroker(GradleProviderInfo provider, Project project, PactPluginExtension ext) {
    if (ext.broker && project.gradle.startParameter.taskNames.any {
      it.toLowerCase().contains(PACT_VERIFY.toLowerCase()) }) {
      def options = [:]
      if (ext.broker.pactBrokerUsername) {
        options.authentication = ['basic', ext.broker.pactBrokerUsername, ext.broker.pactBrokerPassword]
      } else if (ext.broker.pactBrokerToken) {
        options.authentication = ['bearer', ext.broker.pactBrokerToken]
      }
      if (provider.brokerConfig.enablePending) {
        options.enablePending = true
        options.providerTags = provider.brokerConfig.providerTags
      }
      try {
        provider.consumers = provider.hasPactsFromPactBrokerWithSelectorsV2(options, ext.broker.pactBrokerUrl,
          provider.brokerConfig.selectors)
      } catch (RuntimeException ex) {
        throw new GradleScriptException("Failed to fetch pacts from pact broker ${ext.broker.pactBrokerUrl}",
          ex)
      }
    }
  }
}
