/*
 * Copyright © 2017 VMware, Inc. All Rights Reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */

package com.vmware.connectors.github.pr;

import com.google.common.collect.ImmutableList;
import com.vmware.connectors.mock.MockRestServiceServer;
import com.vmware.connectors.test.ControllerTestsBase;
import com.vmware.connectors.test.JsonReplacementsBuilder;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static com.vmware.connectors.test.JsonSchemaValidator.isValidHeroCardConnectorResponse;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GithubPrControllerTest extends ControllerTestsBase {

    private static final String GITHUB_AUTH_TOKEN = "test-auth-token";

    private MockRestServiceServer mockGithub;

    @BeforeEach
    void init() throws Exception {
        super.setup();

        mockGithub = MockRestServiceServer.bindTo(requestHandlerHolder)
                .ignoreExpectOrder(true)
                .build();
    }

    @AfterEach
    void teardown() throws Exception {
        mockGithub.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/cards/requests",
            "/api/v1/test-owner/test-repo/1234/close",
            "/api/v1/test-owner/test-repo/1234/merge",
            "/api/v1/test-owner/test-repo/1234/approve",
            "/api/v1/test-owner/test-repo/1234/comment",
            "/api/v1/test-owner/test-repo/1234/request-changes"})
    void testProtectedResource(String uri) throws Exception {
        testProtectedResource(POST, uri);
    }

    @Test
    void testDiscovery() throws Exception {
        testConnectorDiscovery();
    }

    @Test
    void testRegex() throws Exception {
        List<String> expected = ImmutableList.of(
                /*
                 * There are 3 links because of the patch and diff links.  The
                 * Set<String> in the cards contract will take care of the
                 * duplication.
                 */
                "https://github.com/vmware/connectors-workspace-one/pull/3",
                "https://github.com/vmware/connectors-workspace-one/pull/3",
                "https://github.com/vmware/connectors-workspace-one/pull/3"
        );
        testRegex("pull_request_urls", fromFile("fake/regex/pr-email.txt"), expected);
    }

    private MockHttpServletRequestBuilder setupPostRequest(
            String path,
            MediaType contentType,
            String authToken,
            String content
    ) throws Exception {
        return setupPostRequest(path, contentType, authToken, content, null);
    }

    private MockHttpServletRequestBuilder setupPostRequest(
            String path,
            MediaType contentType,
            String authToken,
            String content,
            String language
    ) throws Exception {

        MockHttpServletRequestBuilder builder = post(path)
                .with(token(accessToken()))
                .contentType(contentType)
                .accept(APPLICATION_JSON)
                .header("x-github-pr-base-url", "https://api.github.com")
                .header("x-routing-prefix", "https://hero/connectors/github-pr/")
                .content(content);

        if (authToken != null) {
            builder = builder.header("x-github-pr-authorization", "Bearer " + authToken);
        }

        if (language != null) {
            builder = builder.header(ACCEPT_LANGUAGE, language);
        }

        return builder;
    }

    private ResultActions requestCards(String authToken, String content) throws Exception {
        return requestCards(authToken, content, null);
    }

    private ResultActions requestCards(String authToken, String content, String language) throws Exception {
        return perform(
                setupPostRequest(
                        "/cards/requests",
                        APPLICATION_JSON,
                        authToken,
                        content,
                        language
                )
        );
    }

    private ResultActions close(String authToken, String reason) throws Exception {
        return perform(
                setupPostRequest(
                        "/api/v1/vmware/test-repo/99/close",
                        APPLICATION_FORM_URLENCODED,
                        authToken,
                        reason == null ? "" : String.format("reason=%s", reason)
                )
        );
    }

    private ResultActions merge(String authToken, String sha) throws Exception {
        return perform(
                setupPostRequest(
                        "/api/v1/vmware/test-repo/99/merge",
                        APPLICATION_FORM_URLENCODED,
                        authToken,
                        sha == null ? "" : String.format("sha=%s", sha)
                )
        );
    }

    private ResultActions approve(String authToken) throws Exception {
        return perform(
                setupPostRequest(
                        "/api/v1/vmware/test-repo/99/approve",
                        APPLICATION_FORM_URLENCODED,
                        authToken,
                        ""
                )
        );
    }

    private ResultActions comment(String authToken, String message) throws Exception {
        return perform(
                setupPostRequest(
                        "/api/v1/vmware/test-repo/99/comment",
                        APPLICATION_FORM_URLENCODED,
                        authToken,
                        message == null ? "" : String.format("message=%s", message)
                )
        );
    }

    private ResultActions requestChanges(String authToken, String request) throws Exception {
        return perform(
                setupPostRequest(
                        "/api/v1/vmware/test-repo/99/request-changes",
                        APPLICATION_FORM_URLENCODED,
                        authToken,
                        request == null ? "" : String.format("request=%s", request)
                )
        );
    }

    /////////////////////////////
    // Request Cards
    /////////////////////////////

    @Test
    void testRequestCardsUnauthorized() throws Exception {
        mockGithub.expect(ExpectedCount.manyTimes(), requestTo(any(String.class)))
                .andRespond(withUnauthorizedRequest());

        requestCards(GITHUB_AUTH_TOKEN, fromFile("requests/valid/cards/card.json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Backend-Status", "401"));
    }

    @Test
    void testRequestCardsAuthHeaderMissing() throws Exception {
        requestCards(null, fromFile("requests/valid/cards/card.json"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Card request success cases")
    @ParameterizedTest(name = "{index} ==> Language=''{0}''")
    @CsvSource({
            StringUtils.EMPTY + ", responses/success/cards/card.json",
            "xx, responses/success/cards/card_xx.json"})
    void testRequestCardsSuccess(String acceptLanguage, String responseFile) throws Exception {
        trainGithubForCards();

        requestCards(GITHUB_AUTH_TOKEN, fromFile("requests/valid/cards/card.json"), acceptLanguage)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().string(isValidHeroCardConnectorResponse()))
                .andExpect(
                        content().string(
                                JsonReplacementsBuilder
                                        .from(fromFile(responseFile))
                                        .buildForCards()
                        )
                );
    }

    private void trainGithubForCards() throws Exception {
        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/1"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(GET))
                .andRespond(withSuccess(fromFile("fake/cards/small-merged-pr.json"), APPLICATION_JSON));

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/2"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(GET))
                .andRespond(withSuccess(fromFile("fake/cards/small-open-pr.json"), APPLICATION_JSON));

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/3"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(GET))
                .andRespond(withSuccess(fromFile("fake/cards/big-closed-pr.json"), APPLICATION_JSON));

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/0-not-found"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND));
    }

    @Test
    void testRequestCardsEmptyPrUrlsSuccess() throws Exception {
        requestCards(GITHUB_AUTH_TOKEN, fromFile("requests/valid/cards/empty-pr-urls.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().string(isValidHeroCardConnectorResponse()))
                .andExpect(
                        content().string(
                                JsonReplacementsBuilder
                                        .from(fromFile("responses/success/cards/empty-pr-urls.json"))
                                        .buildForCards()
                        )
                );
    }

    @Test
    void testRequestCardsMissingPrUrls() throws Exception {
        requestCards(GITHUB_AUTH_TOKEN, fromFile("requests/valid/cards/missing-pr-urls.json"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Card request invalid token cases")
    @ParameterizedTest(name = "{index} ==> ''{0}''")
    @CsvSource({"requests/invalid/cards/empty-tokens.json, responses/error/cards/empty-tokens.json",
            "requests/invalid/cards/missing-tokens.json, responses/error/cards/missing-tokens.json"})
    void testRequestCardsInvalidTokens(String reqFile, String resFile) throws Exception {
        requestCards(GITHUB_AUTH_TOKEN, fromFile(reqFile))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(fromFile(resFile), false));
    }

    /////////////////////////////
    // Approve Action
    /////////////////////////////

    @Test
    void testApproveActionUnauthorized() throws Exception {
        mockGithub.expect(requestTo(any(String.class)))
                .andRespond(withUnauthorizedRequest());

        approve(GITHUB_AUTH_TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Backend-Status", "401"));
    }

    @Test
    void testApproveAuthHeaderMissing() throws Exception {
        approve(null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void testApproveActionSuccess() throws Exception {
        String fakeResponse = fromFile("fake/actions/approve/success.json");

        String expected = fromFile("responses/actions/approve/success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                // Note: This doesn't verify that we don't get an explicit null, see SPR-16339
                .andExpect(MockRestRequestMatchers.jsonPath("$.body").doesNotExist())
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("APPROVE")))
                .andRespond(withSuccess(fakeResponse, APPLICATION_JSON));

        approve(GITHUB_AUTH_TOKEN)
                .andExpect(status().isOk())
                .andExpect(content().json(expected, false));
    }

    @Test
    void testApproveActionFailed() throws Exception {
        String fakeResponse = fromFile("fake/actions/approve/failed.json");

        String expected = fromFile("responses/actions/approve/failed.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                // Note: This doesn't verify that we don't get an explicit null, see SPR-16339
                .andExpect(MockRestRequestMatchers.jsonPath("$.body").doesNotExist())
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("APPROVE")))
                .andRespond(
                        withStatus(UNPROCESSABLE_ENTITY)
                                .contentType(APPLICATION_JSON)
                                .body(fakeResponse)
                );

        approve(GITHUB_AUTH_TOKEN)
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("x-backend-status", "422"))
                .andExpect(content().json(expected, false));
    }

    /////////////////////////////
    // Close Action
    /////////////////////////////

    @Test
    void testCloseActionUnauthorized() throws Exception {
        mockGithub.expect(requestTo(any(String.class)))
                .andRespond(withUnauthorizedRequest());

        close(GITHUB_AUTH_TOKEN, "test-close-reason")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Backend-Status", "401"));
    }

    @Test
    void testCloseAuthHeaderMissing() throws Exception {
        close(null, "test-close-reason")
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCloseActionSuccess() throws Exception {
        String fakeCommentResponse = fromFile("fake/actions/close/comment-success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-close-reason")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("COMMENT")))
                .andRespond(withSuccess(fakeCommentResponse, APPLICATION_JSON));

        String fakeCloseResponse = fromFile("fake/actions/close/close-success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(PATCH))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.state", is("closed")))
                .andRespond(withSuccess(fakeCloseResponse, APPLICATION_JSON));

        String expected = fromFile("responses/actions/close/close-success.json");

        close(GITHUB_AUTH_TOKEN, "test-close-reason")
                .andExpect(status().isOk())
                .andExpect(content().json(expected, false));
    }

    @Test
    void testCloseActionNoReasonSuccess() throws Exception {
        String fakeResponse = fromFile("fake/actions/close/close-success-no-reason.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(PATCH))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.state", is("closed")))
                .andRespond(withSuccess(fakeResponse, APPLICATION_JSON));

        String expected = fromFile("responses/actions/close/close-success-no-reason.json");

        close(GITHUB_AUTH_TOKEN, null)
                .andExpect(status().isOk())
                .andExpect(content().json(expected, false));
    }

    @Test
    void testCloseActionCommentFailed() throws Exception {
        String fakeResponse = fromFile("fake/actions/close/comment-failed.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-close-reason")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("COMMENT")))
                .andRespond(
                        withStatus(NOT_FOUND)
                                .contentType(APPLICATION_JSON)
                                .body(fakeResponse)
                );

        String expected = fromFile("responses/actions/close/comment-failed.json");

        close(GITHUB_AUTH_TOKEN, "test-close-reason")
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("x-backend-status", "404"))
                .andExpect(content().json(expected, false));
    }

    @Test
    void testCloseActionCloseFailed() throws Exception {
        String fakeCommentResponse = fromFile("fake/actions/close/comment-success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-close-reason")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("COMMENT")))
                .andRespond(withSuccess(fakeCommentResponse, APPLICATION_JSON));

        String fakeCloseResponse = fromFile("fake/actions/close/close-failed.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(PATCH))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.state", is("closed")))
                .andRespond(
                        withStatus(SERVICE_UNAVAILABLE)
                                .contentType(APPLICATION_JSON)
                                .body(fakeCloseResponse)
                );

        String expected = fromFile("responses/actions/close/close-failed.json");

        close(GITHUB_AUTH_TOKEN, "test-close-reason")
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("x-backend-status", "503"))
                .andExpect(content().json(expected, false));
    }

    /////////////////////////////
    // Comment Action
    /////////////////////////////

    @Test
    void testCommentActionUnauthorized() throws Exception {
        mockGithub.expect(requestTo(any(String.class)))
                .andRespond(withUnauthorizedRequest());

        comment(GITHUB_AUTH_TOKEN, "test-comment")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Backend-Status", "401"));
    }

    @Test
    void testCommentAuthHeaderMissing() throws Exception {
        comment(null, "test-comment")
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCommentActionSuccess() throws Exception {
        String fakeResponse = fromFile("fake/actions/comment/success.json");

        String expected = fromFile("responses/actions/comment/success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-comment")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("COMMENT")))
                .andRespond(withSuccess(fakeResponse, APPLICATION_JSON));

        comment(GITHUB_AUTH_TOKEN, "test-comment")
                .andExpect(status().isOk())
                .andExpect(content().json(expected, false));
    }

    @Test
    void testCommentActionMissingComment() throws Exception {
        comment(GITHUB_AUTH_TOKEN, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCommentActionFailed() throws Exception {
        String fakeResponse = fromFile("fake/actions/comment/failed.json");

        String expected = fromFile("responses/actions/comment/failed.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-comment")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("COMMENT")))
                .andRespond(
                        withStatus(NOT_FOUND)
                                .contentType(APPLICATION_JSON)
                                .body(fakeResponse)
                );

        comment(GITHUB_AUTH_TOKEN, "test-comment")
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("x-backend-status", "404"))
                .andExpect(content().json(expected, false));
    }

    /////////////////////////////
    // Request Changes Action
    /////////////////////////////

    @Test
    void testRequestChangesActionUnauthorized() throws Exception {
        mockGithub.expect(requestTo(any(String.class)))
                .andRespond(withUnauthorizedRequest());

        requestChanges(GITHUB_AUTH_TOKEN, "test-request-changes")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Backend-Status", "401"));
    }

    @Test
    void testRequestChangesAuthHeaderMissing() throws Exception {
        requestChanges(null, "test-request-changes")
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRequestChangesActionSuccess() throws Exception {
        String fakeResponse = fromFile("fake/actions/request-changes/success.json");

        String expected = fromFile("responses/actions/request-changes/success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-request-changes")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("REQUEST_CHANGES")))
                .andRespond(withSuccess(fakeResponse, APPLICATION_JSON));

        requestChanges(GITHUB_AUTH_TOKEN, "test-request-changes")
                .andExpect(status().isOk())
                .andExpect(content().json(expected, false));
    }

    @Test
    void testRequestChangesActionMissingRequestChanges() throws Exception {
        requestChanges(GITHUB_AUTH_TOKEN, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRequestChangesActionFailed() throws Exception {
        String fakeResponse = fromFile("fake/actions/request-changes/failed.json");

        String expected = fromFile("responses/actions/request-changes/failed.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/reviews"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(POST))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.body", is("test-requested-changes")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.event", is("REQUEST_CHANGES")))
                .andRespond(
                        withStatus(NOT_FOUND)
                                .contentType(APPLICATION_JSON)
                                .body(fakeResponse)
                );

        requestChanges(GITHUB_AUTH_TOKEN, "test-requested-changes")
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("x-backend-status", "404"))
                .andExpect(content().json(expected, false));
    }

    /////////////////////////////
    // Merge Action
    /////////////////////////////

    @Test
    void testMergeActionUnauthorized() throws Exception {
        mockGithub.expect(requestTo(any(String.class)))
                .andRespond(withUnauthorizedRequest());

        merge(GITHUB_AUTH_TOKEN, "test-sha")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Backend-Status", "401"));
    }

    @Test
    void testMergeAuthHeaderMissing() throws Exception {
        merge(null, "test-sha")
                .andExpect(status().isBadRequest());
    }

    @Test
    void testMergeActionMissingSha() throws Exception {
        merge(GITHUB_AUTH_TOKEN, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void testMergeActionSuccess() throws Exception {
        String fakeResponse = fromFile("fake/actions/merge/success.json");

        String expected = fromFile("responses/actions/merge/success.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/merge"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(PUT))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.sha", is("test-sha")))
                .andRespond(withSuccess(fakeResponse, APPLICATION_JSON));

        merge(GITHUB_AUTH_TOKEN, "test-sha")
                .andExpect(status().isOk())
                .andExpect(content().json(expected, false));
    }

    @Test
    void testMergeActionFailed() throws Exception {
        String fakeResponse = fromFile("fake/actions/merge/failed.json");

        String expected = fromFile("responses/actions/merge/failed.json");

        mockGithub.expect(requestTo("https://api.github.com/repos/vmware/test-repo/pulls/99/merge"))
                .andExpect(header(AUTHORIZATION, "Bearer " + GITHUB_AUTH_TOKEN))
                .andExpect(method(PUT))
                .andExpect(MockRestRequestMatchers.content().contentType(APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.jsonPath("$.sha", is("test-sha")))
                .andRespond(
                        withStatus(METHOD_NOT_ALLOWED)
                                .contentType(APPLICATION_JSON)
                                .body(fakeResponse)
                );

        merge(GITHUB_AUTH_TOKEN, "test-sha")
                .andExpect(status().is5xxServerError())
                .andExpect(header().string("x-backend-status", "405"))
                .andExpect(content().json(expected, false));
    }

}
