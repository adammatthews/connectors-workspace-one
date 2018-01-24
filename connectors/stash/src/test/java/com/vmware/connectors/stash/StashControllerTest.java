package com.vmware.connectors.stash;

import com.google.common.collect.ImmutableList;
import com.vmware.connectors.stash.utils.StashAction;
import com.vmware.connectors.test.ControllerTestsBase;
import com.vmware.connectors.test.JsonReplacementsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.AsyncRestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vmware.connectors.stash.utils.StashConstants.ATLASSIAN_TOKEN;
import static com.vmware.connectors.stash.utils.StashConstants.COMMENT_PARAM_KEY;
import static com.vmware.connectors.test.JsonSchemaValidator.isValidHeroCardConnectorResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class StashControllerTest extends ControllerTestsBase {

    private static final String STASH_AUTH_TOKEN = "stash-token";

    private static final String PULL_REQUEST_ID_1 = "236";

    private static final String PULL_REQUEST_ID_2 = "246";

    @Autowired
    private AsyncRestTemplate rest;

    private MockRestServiceServer mockStash;

    @Value("classpath:stash/responses/pr_236.json")
    private Resource pr236;

    @Value("classpath:stash/responses/pr_246.json")
    private Resource pr246;

    @Value("classpath:stash/responses/approved.json")
    private Resource approve;

    @Value("classpath:stash/responses/comments.json")
    private Resource comments;

    @Value("classpath:stash/responses/declined.json")
    private Resource declined;

    @Value("classpath:stash/responses/merged.json")
    private Resource merged;

    @Before
    public void setup() throws Exception {
        super.setup();

        mockStash = MockRestServiceServer.bindTo(rest)
                .ignoreExpectOrder(true)
                .build();
    }

    @Test
    public void testProtectedResources() throws Exception {
        testProtectedResource(POST, "/cards/requests");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/approve");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/merge");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/comments");
        testProtectedResource(POST, "/api/v1/UFO/app-platform-server/249/decline");
    }

    @Test
    public void testDiscovery() throws Exception {
        testConnectorDiscovery();
    }

    @Test
    public void testRegex() throws Exception {
        final List<String> expectedList = ImmutableList.of(
                // Project Name/ Repository Plug/ Pull request id.
                "UFO/app-platform-server/244",
                "UFO/app-platform-server/245",
                "UFO/app-platform-server/241",
                "UFO/app-platform-server/239",
                "UFO/card-connectors/9");

        mvc.perform(
                get("/discovery/metadata.hal")
                        .with(token(accessToken()))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(mvcResult -> {
            final String json = mvcResult.getResponse().getContentAsString();
            final Map<String, Object> results = mapper.readValue(json, Map.class);
            final Map<String, Object> fields = (Map<String, Object>) results.get("fields");
            final Map<String, Object> prEmailSubject = (Map<String, Object>) fields.get("pr_email_subject");
            final String regex = (String) prEmailSubject.get("regex");

            verifyRegEx(regex, fromFile("/regex/pr-email-subject.txt"), expectedList);
        });
    }

    private void verifyRegEx(final String regex,
                             final String emailSubjectLists,
                             final List<String> expectedList) {

        final Pattern pattern = Pattern.compile(regex);
        final List<String> resultList = new ArrayList<>();

        for (String emailSubject : emailSubjectLists.split("\\n")) {
            final Matcher matcher = pattern.matcher(emailSubject);
            while (matcher.find()) {
                final String projectKey = matcher.group(2);
                final String repositorySlug = matcher.group(3);
                final String pullRequestId = matcher.group(4);

                final String result = projectKey + "/" + repositorySlug + "/" + pullRequestId;
                resultList.add(result);
            }
        }
        assertThat(resultList, equalTo(expectedList));
    }

    @Test
    public void getImage() throws Exception {
        perform(get("/images/connector.png"))
                .andExpect(status().isOk())
                .andExpect(header().longValue(CONTENT_LENGTH, 18453))
                .andExpect(header().string(CONTENT_TYPE, IMAGE_PNG_VALUE))
                .andExpect(content().bytes(bytesFromFile("/static/images/connector.png")));
    }

    @Test
    public void testCardRequestWithEmptyIssue() throws Exception {
        testCardRequests("emptyIssue.json", "emptyIssue.json");
    }

    @Test
    public void testCardRequests() throws Exception {
        expect(PULL_REQUEST_ID_1).andRespond(withSuccess(pr236, APPLICATION_JSON));
        expect(PULL_REQUEST_ID_2).andRespond(withSuccess(pr246, APPLICATION_JSON));

        testCardRequests("request.json", "success.json");

        this.mockStash.verify();
    }

    @Test
    public void testRequestEmptyCards() throws Exception {
        testRequestCardsWithMissingParameter("emptyRequest.json", "emptyRequest.json");
    }

    @Test
    public void testRequestEmptyToken() throws Exception {
        testRequestCardsWithMissingParameter("emptyToken.json", "emptyToken.json");
    }

    @Test
    public void approve() throws Exception {
        final String url = "/api/v1/UFO/app-platform-server/236/approve";

        testStashPRAction(url, approve, StashAction.APPROVE);
    }

    @Test
    public void decline() throws Exception {
        final String url = "/api/v1/UFO/app-platform-server/236/decline";

        testStashPRAction(url, declined, StashAction.DECLINE);
    }

    @Test
    public void merge() throws Exception {
        final String url = "/api/v1/UFO/app-platform-server/236/merge";

        testStashPRAction(url, merged, StashAction.MERGE);
    }

    @Test
    public void comment() throws Exception {
        this.mockStash.expect(requestTo("https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/236/comments"))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.header(AUTHORIZATION, "Basic " + STASH_AUTH_TOKEN))
                .andExpect(MockRestRequestMatchers.header(CONTENT_TYPE, APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(comments, APPLICATION_JSON));

        perform(post("/api/v1/UFO/app-platform-server/236/comments")
                .with(token(accessToken()))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .header("x-stash-authorization", "Basic " + STASH_AUTH_TOKEN)
                .header("x-stash-base-url", "https://stash.air-watch.com")
                .param(COMMENT_PARAM_KEY, "Pull request comment")
                ).andExpect(status().isOk());
    }

    private void testStashPRAction(final String url,
                                   final Resource resource,
                                   final StashAction stashAction) throws Exception {
        expect(PULL_REQUEST_ID_1).andRespond(withSuccess(pr236, APPLICATION_JSON));

        this.mockStash.expect(requestTo("https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/236/" + stashAction.getAction() + "?version=10"))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.header(AUTHORIZATION, "Basic " + STASH_AUTH_TOKEN))
                .andExpect(MockRestRequestMatchers.header(ATLASSIAN_TOKEN, "no-check"))
                .andRespond(withSuccess(resource, APPLICATION_JSON));

        perform(post(url)
                .with(token(accessToken()))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .header("x-stash-authorization", "Basic " + STASH_AUTH_TOKEN)
                .header("x-stash-base-url", "https://stash.air-watch.com")
                .header("x-routing-prefix", "https://hero/connectors/stash/"))
                .andExpect(status().isOk());

    }

    private void testRequestCardsWithMissingParameter(String requestFile, String responseFile) throws Exception {
        MockHttpServletRequestBuilder builder = requestCard(STASH_AUTH_TOKEN, requestFile);

        perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(fromFile("stash/responses/" + responseFile)));
    }

    private void testCardRequests(final String requestFile, final String responseFile) throws Exception {
        final MockHttpServletRequestBuilder builder = requestCard(STASH_AUTH_TOKEN, requestFile);
        perform(builder)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().string(isValidHeroCardConnectorResponse()))
                .andExpect(content().string(JsonReplacementsBuilder.from(
                        fromFile("stash/responses/" + responseFile)).buildForCards()))
                .andReturn();
    }

    private MockHttpServletRequestBuilder requestCard(final String authToken, final String requestFile) throws IOException {
        return post("/cards/requests").with(token(accessToken()))
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .header("x-stash-authorization", "Basic " + authToken)
                .header("x-stash-base-url", "https://stash.air-watch.com")
                .header("x-routing-prefix", "https://hero/connectors/stash/")
                .content(fromFile("/stash/requests/" + requestFile));
    }

    private ResponseActions expect(final String pullRequestId) {
        return this.mockStash.expect(requestTo("https://stash.air-watch.com/rest/api/1.0/projects/UFO/repos/app-platform-server/pull-requests/" + pullRequestId))
                .andExpect(method(GET))
                .andExpect(MockRestRequestMatchers.header(AUTHORIZATION, "Basic stash-token"));
    }
}
