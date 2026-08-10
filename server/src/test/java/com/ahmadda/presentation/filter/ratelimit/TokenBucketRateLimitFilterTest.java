package com.ahmadda.presentation.filter.ratelimit;

import com.ahmadda.infra.auth.jwt.JwtProvider;
import com.ahmadda.infra.auth.jwt.config.JwtAccessTokenProperties;
import com.ahmadda.infra.auth.jwt.dto.JwtMemberPayload;
import com.ahmadda.presentation.header.HeaderProvider;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TokenBucketRateLimitFilterTest {

    TokenBucketRateLimitFilter filter;
    LoadingCache<Long, Bucket> memberRateLimitBuckets;
    RateLimitExceededHandler rateLimitExceededHandler;
    HeaderProvider headerProvider;
    JwtProvider jwtProvider;
    JwtAccessTokenProperties jwtAccessTokenProperties;

    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        memberRateLimitBuckets = mock(LoadingCache.class);
        rateLimitExceededHandler = spy(new RateLimitExceededHandler(new ObjectMapper()));
        headerProvider = mock(HeaderProvider.class);
        jwtProvider = mock(JwtProvider.class);

        var accessSecretKey = UUID.randomUUID()
                .toString();
        var accessExpiration = Duration.ofHours(1);
        jwtAccessTokenProperties = new JwtAccessTokenProperties(accessSecretKey, accessExpiration);

        filter = new TokenBucketRateLimitFilter(
                memberRateLimitBuckets,
                rateLimitExceededHandler,
                headerProvider,
                jwtProvider,
                jwtAccessTokenProperties
        );

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void 비회원요청은_제한없이_통과된다() throws Exception {
        // given
        request.removeHeader(HttpHeaders.AUTHORIZATION);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimitExceededHandler, memberRateLimitBuckets);
    }

    @Test
    void 유효한_토큰은_버킷에서_소비를_시도하고_통과된다() throws Exception {
        // given
        var memberId = 1L;
        var token = "token";
        var bearerToken = "Bearer " + token;

        request.addHeader(HttpHeaders.AUTHORIZATION, bearerToken);

        var payload = mock(JwtMemberPayload.class);
        when(payload.memberId()).thenReturn(memberId);
        when(headerProvider.extractAccessToken(bearerToken)).thenReturn(token);
        when(jwtProvider.parsePayload(token, jwtAccessTokenProperties.getAccessSecretKey())).thenReturn(payload);

        var bucket = mock(Bucket.class);
        var probe = mock(ConsumptionProbe.class);

        when(memberRateLimitBuckets.get(memberId)).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        verify(bucket).tryConsumeAndReturnRemaining(1);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(rateLimitExceededHandler);
    }

    @Test
    void 요청한도초과시_핸들러가_호출된다() throws Exception {
        // given
        var memberId = 1L;
        var token = "token";
        var bearerToken = "Bearer " + token;

        request.addHeader(HttpHeaders.AUTHORIZATION, bearerToken);

        var payload = mock(JwtMemberPayload.class);
        when(payload.memberId()).thenReturn(memberId);
        when(headerProvider.extractAccessToken(bearerToken)).thenReturn(token);
        when(jwtProvider.parsePayload(token, jwtAccessTokenProperties.getAccessSecretKey())).thenReturn(payload);

        var bucket = mock(Bucket.class);
        var probe = mock(ConsumptionProbe.class);

        when(memberRateLimitBuckets.get(memberId)).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(TimeUnit.SECONDS.toNanos(5));

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        verify(rateLimitExceededHandler).handle(eq(request), eq(response), eq(5L));
        verifyNoInteractions(chain);
    }
}
