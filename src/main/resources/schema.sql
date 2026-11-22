CREATE TABLE IF NOT EXISTS demo_users (
    id BIGINT PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS movies (
    id BIGINT PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    genre VARCHAR(60) NOT NULL,
    release_year INT NOT NULL,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS ratings (
    user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    score INT NOT NULL,
    PRIMARY KEY (user_id, movie_id),
    CONSTRAINT fk_ratings_user FOREIGN KEY (user_id) REFERENCES demo_users (id),
    CONSTRAINT fk_ratings_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT ck_ratings_score CHECK (score BETWEEN 1 AND 5)
);
