package com.gymsync.api.exercise.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsync.api.exercise.config.MediaProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Looks up Wikimedia Commons images and (optionally) YouTube Data API demos. Failures are logged
 * and treated as empty results so suggestion generation never depends on third-party uptime.
 */
@Component
public class HttpMediaSearchClient implements MediaSearchClient {

  private static final Logger log = LoggerFactory.getLogger(HttpMediaSearchClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(8);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final MediaProperties mediaProperties;

  public HttpMediaSearchClient(ObjectMapper objectMapper, MediaProperties mediaProperties) {
    this.objectMapper = objectMapper;
    this.mediaProperties = mediaProperties;
    this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  @Override
  public Optional<WikimediaImageResult> findWikimediaImage(String exerciseName) {
    if (!StringUtils.hasText(exerciseName)) {
      return Optional.empty();
    }
    try {
      String searchQuery = exerciseName.trim() + " exercise";
      String searchUrl =
          "https://commons.wikimedia.org/w/api.php?action=query&list=search&srsearch="
              + encode(searchQuery)
              + "&srnamespace=6&srlimit=1&format=json";
      JsonNode searchRoot = getJson(searchUrl);
      JsonNode searchHits = searchRoot.path("query").path("search");
      if (!searchHits.isArray() || searchHits.isEmpty()) {
        return Optional.empty();
      }
      String title = searchHits.get(0).path("title").asText(null);
      if (!StringUtils.hasText(title)) {
        return Optional.empty();
      }
      String infoUrl =
          "https://commons.wikimedia.org/w/api.php?action=query&titles="
              + encode(title)
              + "&prop=imageinfo&iiprop=url&format=json";
      JsonNode infoRoot = getJson(infoUrl);
      JsonNode pages = infoRoot.path("query").path("pages");
      if (!pages.isObject() || pages.isEmpty()) {
        return Optional.empty();
      }
      JsonNode page = pages.elements().next();
      String imageUrl = page.path("imageinfo").path(0).path("url").asText(null);
      if (!StringUtils.hasText(imageUrl)) {
        return Optional.empty();
      }
      return Optional.of(new WikimediaImageResult(imageUrl, title));
    } catch (Exception ex) {
      log.warn("Wikimedia lookup failed for '{}': {}", exerciseName, ex.toString());
      return Optional.empty();
    }
  }

  @Override
  public Optional<YouTubeVideoResult> findYouTubeDemo(String exerciseName) {
    if (!mediaProperties.hasYoutubeApiKey() || !StringUtils.hasText(exerciseName)) {
      return Optional.empty();
    }
    try {
      String q = exerciseName.trim() + " exercise form demonstration";
      String url =
          "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1&q="
              + encode(q)
              + "&key="
              + encode(mediaProperties.getYoutubeApiKey());
      JsonNode root = getJson(url);
      JsonNode items = root.path("items");
      if (!items.isArray() || items.isEmpty()) {
        return Optional.empty();
      }
      JsonNode first = items.get(0);
      String videoId = first.path("id").path("videoId").asText(null);
      String title = first.path("snippet").path("title").asText("YouTube demonstration");
      if (!StringUtils.hasText(videoId)) {
        return Optional.empty();
      }
      return Optional.of(
          new YouTubeVideoResult("https://www.youtube.com/watch?v=" + videoId, title));
    } catch (Exception ex) {
      log.warn("YouTube Data API lookup failed for '{}': {}", exerciseName, ex.toString());
      return Optional.empty();
    }
  }

  private JsonNode getJson(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("User-Agent", "GymSync/0.1 (portfolio; media-suggestions)")
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("HTTP " + response.statusCode());
    }
    return objectMapper.readTree(response.body());
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
