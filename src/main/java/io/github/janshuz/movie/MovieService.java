package io.github.janshuz.movie;

import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import io.github.janshuz.movie.MovieStore.DemoUser;
import io.github.janshuz.movie.MovieStore.Movie;
import io.github.janshuz.movie.RecommendationEngine.Rating;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class MovieService {

    record Result(Movie movie, double score, String source, int neighborCount) {}

    private final MovieStore store;
    private final int maxNeighbors;

    MovieService(MovieStore store, @Value("${movie.recommendation.max-neighbors}") int maxNeighbors) {
        if (maxNeighbors < 1) {
            throw new IllegalArgumentException("movie.recommendation.max-neighbors must be positive");
        }
        this.store = store;
        this.maxNeighbors = maxNeighbors;
    }

    List<Movie> movies(String query) {
        String keyword = query.strip().toLowerCase(Locale.ROOT);
        return store.movies().stream()
                .filter(movie -> movie.title().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
    }

    List<DemoUser> users() {
        return store.users();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Result> recommendations(Long userId, int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
        }

        var results = new ArrayList<Result>(limit);
        var movies = store.movies();
        var excluded = new HashSet<Long>();
        if (userId != null) {
            requireUser(userId);
            var ratings = store.ratings();
            excluded.addAll(ratings.stream().filter(rating -> rating.userId() == userId)
                    .map(Rating::movieId).collect(toSet()));
            var moviesById = movies.stream().collect(toMap(Movie::id, movie -> movie));

            for (var recommendation : RecommendationEngine.recommend(ratings, userId, limit, maxNeighbors)) {
                results.add(new Result(moviesById.get(recommendation.movieId()), recommendation.predictedScore(),
                        "collaborative", recommendation.neighborCount()));
                excluded.add(recommendation.movieId());
            }
        }

        movies.stream()
                .filter(movie -> movie.ratingCount() > 0 && !excluded.contains(movie.id()))
                .sorted(comparingDouble(Movie::averageRating).reversed()
                        .thenComparing(comparingInt(Movie::ratingCount).reversed())
                        .thenComparingLong(Movie::id))
                .limit(limit - results.size())
                .map(movie -> new Result(movie, movie.averageRating(), "popular", 0))
                .forEach(results::add);
        return results;
    }

    @Transactional
    public void rate(long userId, long movieId, Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be an integer from 1 to 5");
        }
        requireUser(userId);
        if (store.movies().stream().noneMatch(movie -> movie.id() == movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found");
        }
        store.upsertRating(userId, movieId, score);
    }

    private void requireUser(long userId) {
        if (users().stream().noneMatch(user -> user.id() == userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo user not found");
        }
    }
}
