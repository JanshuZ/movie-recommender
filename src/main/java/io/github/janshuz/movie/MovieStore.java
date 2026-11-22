package io.github.janshuz.movie;

import io.github.janshuz.movie.RecommendationEngine.Rating;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MovieStore {
    record Movie(long id, String title, String genre, int year, String description,
                 double averageRating, int ratingCount) {}

    record DemoUser(long id, String name) {}

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class, id = true),
        @Arg(column = "title", javaType = String.class),
        @Arg(column = "genre", javaType = String.class),
        @Arg(column = "release_year", javaType = int.class),
        @Arg(column = "description", javaType = String.class),
        @Arg(column = "average_rating", javaType = double.class),
        @Arg(column = "rating_count", javaType = int.class)
    })
    @Select("""
            SELECT m.id, m.title, m.genre, m.release_year, m.description,
                   COALESCE(AVG(CAST(r.score AS DECIMAL(10, 6))), 0) AS average_rating,
                   COUNT(r.movie_id) AS rating_count
            FROM movies m
            LEFT JOIN ratings r ON m.id = r.movie_id
            GROUP BY m.id, m.title, m.genre, m.release_year, m.description
            ORDER BY m.id
            """)
    List<Movie> movies();

    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class, id = true),
        @Arg(column = "display_name", javaType = String.class)
    })
    @Select("SELECT id, display_name FROM demo_users ORDER BY id")
    List<DemoUser> users();

    @ConstructorArgs({
        @Arg(column = "user_id", javaType = long.class, id = true),
        @Arg(column = "movie_id", javaType = long.class, id = true),
        @Arg(column = "score", javaType = int.class)
    })
    @Select("SELECT user_id, movie_id, score FROM ratings ORDER BY user_id, movie_id")
    List<Rating> ratings();

    @Insert("""
            INSERT INTO ratings (user_id, movie_id, score)
            VALUES (#{userId}, #{movieId}, #{score})
            ON DUPLICATE KEY UPDATE score = #{score}
            """)
    void upsertRating(@Param("userId") long userId, @Param("movieId") long movieId,
                      @Param("score") int score);
}
