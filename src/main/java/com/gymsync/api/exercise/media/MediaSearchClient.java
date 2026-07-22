package com.gymsync.api.exercise.media;

import java.util.Optional;

/** External media lookup used when generating pending suggestions. */
public interface MediaSearchClient {

  Optional<WikimediaImageResult> findWikimediaImage(String exerciseName);

  Optional<YouTubeVideoResult> findYouTubeDemo(String exerciseName);

  record WikimediaImageResult(String imageUrl, String title) {}

  record YouTubeVideoResult(String videoUrl, String title) {}
}
