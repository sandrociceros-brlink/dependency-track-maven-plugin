package io.github.pmckeown.dependencytrack.metrics;

import com.github.tomakehurst.wiremock.http.Fault;
import io.github.pmckeown.dependencytrack.AbstractDependencyTrackMojoTest;
import io.github.pmckeown.dependencytrack.PollingConfig;
import io.github.pmckeown.dependencytrack.ResourceConstants;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Before;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.github.pmckeown.TestMojoLoader.loadMetricsMojo;
import static io.github.pmckeown.dependencytrack.ResourceConstants.V1_PROJECT;
import static io.github.pmckeown.dependencytrack.TestResourceConstants.V1_METRICS_PROJECT_CURRENT;
import static io.github.pmckeown.dependencytrack.TestUtils.asJson;
import static io.github.pmckeown.dependencytrack.metrics.MetricsBuilder.aMetrics;
import static io.github.pmckeown.dependencytrack.project.ProjectBuilder.aProject;
import static io.github.pmckeown.dependencytrack.project.ProjectListBuilder.aListOfProjects;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class MetricsMojoIntegrationTest extends AbstractDependencyTrackMojoTest {

    private MetricsMojo metricsMojo;

    @Before
    public void setup() throws Exception {
        metricsMojo = loadMetricsMojo(mojoRule);
        metricsMojo.setDependencyTrackBaseUrl("http://localhost:" + wireMockRule.port());
        metricsMojo.setApiKey("abc123");
    }

    @Test
    public void thatMetricsCanBeRetrievedForCurrentProject() throws Exception {
        stubFor(get(urlEqualTo(V1_PROJECT)).willReturn(
                aResponse().withBodyFile("api/v1/project/get-all-projects.json")));

        metricsMojo.setProjectName("testName");
        metricsMojo.setProjectVersion("99.99");

        metricsMojo.execute();

        verify(exactly(1), getRequestedFor(urlEqualTo(ResourceConstants.V1_PROJECT)));
    }

    @Test
    public void thatWhenMetricsAreNotInProjectTheyAreRetrievedExplicitly() throws Exception {
        stubFor(get(urlEqualTo(V1_PROJECT)).willReturn(
                aResponse().withBodyFile("api/v1/project/get-all-projects.json")));
        stubFor(get(urlPathMatching(V1_METRICS_PROJECT_CURRENT)).willReturn(
                aResponse().withBodyFile("api/v1/metrics/project/project-metrics.json")));

        metricsMojo.setProjectName("noMetrics");
        metricsMojo.setProjectVersion("1.0.0");
        metricsMojo.setPollingConfig(PollingConfig.disabled());

        metricsMojo.execute();

        verify(exactly(1), getRequestedFor(urlEqualTo(V1_PROJECT)));
        verify(exactly(1), getRequestedFor(urlPathMatching(V1_METRICS_PROJECT_CURRENT)));
    }

    @Test
    public void thatExceptionIsThrownWhenMetricsCannotBeRetrievedForCurrentProject() throws Exception {
        stubFor(get(urlEqualTo(V1_PROJECT)).willReturn(
                aResponse().withBodyFile("api/v1/project/get-all-projects.json")));
        stubFor(get(urlPathMatching(V1_METRICS_PROJECT_CURRENT)).willReturn(
                aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        metricsMojo.setProjectName("noMetrics");
        metricsMojo.setProjectVersion("1.0.0");
        metricsMojo.setPollingConfig(new PollingConfig(false, 1, 1));

        try {
            metricsMojo.execute();
        } catch (Exception ex) {
            assertThat(ex, is(instanceOf(MojoExecutionException.class)));
        }
    }

    @Test
    public void thatAnyCriticalIssuesPresentCanFailTheBuild() throws Exception {
        stubFor(get(urlEqualTo(V1_PROJECT)).willReturn(
                aResponse().withBody(asJson(
                        aListOfProjects()
                                .withProject(aProject()
                                        .withUuid("1234")
                                        .withName("test-project")
                                        .withVersion("1.2.3")
                                        .withMetrics(
                                                aMetrics()
                                                        .withCritical(101)
                                                        .withHigh(201)
                                                        .withMedium(301)
                                                        .withLow(401)
                                                        .withUnassigned(501)))
                        .build()))));

        metricsMojo.setProjectName("test-project");
        metricsMojo.setProjectVersion("1.2.3");
        metricsMojo.setMetricsThresholds(new MetricsThresholds(100, 200, 300, 400, 500));

        try {
            metricsMojo.execute();
            fail("MojoFailureException expected");
        } catch (Exception ex) {
            assertThat(ex, is(instanceOf(MojoFailureException.class)));
        }
    }

    @Test
    public void thatTheMetricsIsSkippedWhenSkipIsTrue() throws Exception {
        stubFor(get(urlEqualTo(V1_PROJECT)).willReturn(
                aResponse().withBodyFile("api/v1/project/get-all-projects.json")));
        metricsMojo.setSkip(true);
        metricsMojo.setProjectName("testName");
        metricsMojo.setProjectVersion("99.99");

        metricsMojo.execute();

        verify(exactly(0), getRequestedFor(urlEqualTo(V1_PROJECT)));
    }
}
