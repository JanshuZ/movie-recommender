package io.github.janshuz.movie;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecommendationEngine {

    public record Rating(long userId, long movieId, int score) {}

    public record Recommendation(long movieId, double predictedScore, int neighborCount) {}

    private record Neighbor(long userId, double similarity, Map<Long, Integer> ratings) {}

    private static final class Votes {
        private double weightedSum;
        private double totalWeight;
        private int count;

        void add(int score, double similarity) {
            weightedSum += score * similarity;
            totalWeight += similarity;
            count++;
        }

        Recommendation prediction(long movieId) {
            return new Recommendation(movieId, weightedSum / totalWeight, count);
        }
    }

    private RecommendationEngine() {}

    public static List<Recommendation> recommend(
            Collection<Rating> ratings, long userId, int limit, int maxNeighbors) {
        if (limit < 1 || maxNeighbors < 1) {
            throw new IllegalArgumentException("limit and maxNeighbors must be positive");
        }

        var ratingsByUser = new HashMap<Long, Map<Long, Integer>>();
        for (var rating : ratings) {
            ratingsByUser.computeIfAbsent(rating.userId(), ignored -> new HashMap<>())
                    .put(rating.movieId(), rating.score());
        }

        var target = ratingsByUser.remove(userId);
        if (target == null || target.size() < 2) {
            return List.of();
        }

        var nearestNeighbors = ratingsByUser.entrySet().stream()
                .map(entry -> new Neighbor(entry.getKey(), similarity(target, entry.getValue()), entry.getValue()))
                .filter(neighbor -> neighbor.similarity() > 0)
                .sorted(Comparator.comparingDouble(Neighbor::similarity).reversed()
                        .thenComparingLong(Neighbor::userId))
                .limit(maxNeighbors)
                .toList();

        var votesByMovie = new HashMap<Long, Votes>();
        for (var neighbor : nearestNeighbors) {
            for (var rating : neighbor.ratings().entrySet()) {
                if (!target.containsKey(rating.getKey())) {
                    votesByMovie.computeIfAbsent(rating.getKey(), ignored -> new Votes())
                            .add(rating.getValue(), neighbor.similarity());
                }
            }
        }
        return votesByMovie.entrySet().stream()
                .map(entry -> entry.getValue().prediction(entry.getKey()))
                .sorted(Comparator.comparingDouble(Recommendation::predictedScore).reversed()
                        .thenComparing(Comparator.comparingInt(Recommendation::neighborCount).reversed())
                        .thenComparingLong(Recommendation::movieId))
                .limit(limit)
                .toList();
    }

    private static double similarity(Map<Long, Integer> target, Map<Long, Integer> candidate) {
        int overlap = 0;
        double dotProduct = 0;
        double targetSquares = 0;
        double candidateSquares = 0;
        for (var rating : target.entrySet()) {
            Integer otherScore = candidate.get(rating.getKey());
            if (otherScore != null) {
                double targetScore = rating.getValue();
                double candidateScore = otherScore;
                dotProduct += targetScore * candidateScore;
                targetSquares += targetScore * targetScore;
                candidateSquares += candidateScore * candidateScore;
                overlap++;
            }
        }
        if (overlap < 2 || targetSquares == 0 || candidateSquares == 0) {
            return 0;
        }
        return dotProduct / Math.sqrt(targetSquares * candidateSquares);
    }
}
