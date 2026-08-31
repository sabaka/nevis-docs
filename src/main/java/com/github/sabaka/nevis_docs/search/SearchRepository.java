package com.github.sabaka.nevis_docs.search;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SearchRepository {

  private final JdbcClient jdbcClient;

  SearchRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  List<SearchHit> search(
      String query,
      float[] queryEmbedding,
      int candidateLimit,
      int resultLimit,
      int rrfK,
      double maxSemanticDistance) {
    List<SearchHit> hits =
        jdbcClient
            .sql(
                """
                with lexical_candidates as (
                    select entry.entity_type,
                           entry.entity_id,
                           row_number() over (
                               order by ts_rank_cd(entry.lexical_index,
                                            plainto_tsquery('simple', regexp_replace(:query, '[^[:alnum:]]+', ' ', 'g'))) desc,
                                        entry.entity_type,
                                        entry.entity_id) as lexical_rank
                    from search_entry entry
                    where entry.lexical_index @@
                          plainto_tsquery('simple', regexp_replace(:query, '[^[:alnum:]]+', ' ', 'g'))
                    order by lexical_rank
                    limit :candidateLimit
                ),
                semantic_candidates as (
                    select entry.entity_type,
                           entry.entity_id,
                           row_number() over (
                               order by entry.embedding <=> cast(:queryEmbedding as vector),
                                        entry.entity_type,
                                        entry.entity_id) as semantic_rank
                    from search_entry entry
                    where entry.entity_type = 'DOCUMENT'
                      and entry.embedding_status = 'READY'
                      and entry.embedding is not null
                      and (entry.embedding <=> cast(:queryEmbedding as vector)) <= :maxSemanticDistance
                    order by semantic_rank
                    limit :candidateLimit
                ),
                fused as (
                    select coalesce(lexical.entity_type, semantic.entity_type) as entity_type,
                           coalesce(lexical.entity_id, semantic.entity_id)     as entity_id,
                           coalesce(1.0 / (:rrfK + lexical.lexical_rank), 0.0)
                         + coalesce(1.0 / (:rrfK + semantic.semantic_rank), 0.0) as score
                    from lexical_candidates lexical
                    full outer join semantic_candidates semantic
                        on semantic.entity_type = lexical.entity_type
                       and semantic.entity_id = lexical.entity_id
                )
                select entity_type, entity_id, score
                from fused
                order by score desc, entity_type, entity_id
                limit :resultLimit
                """)
            .param("query", query)
            .param("queryEmbedding", VectorLiteral.of(queryEmbedding))
            .param("candidateLimit", candidateLimit)
            .param("resultLimit", resultLimit)
            .param("rrfK", rrfK)
            .param("maxSemanticDistance", maxSemanticDistance)
            .query(
                (resultSet, _) ->
                    new SearchHit(
                        EntityType.valueOf(resultSet.getString("entity_type")),
                        resultSet.getObject("entity_id", UUID.class),
                        resultSet.getDouble("score")))
            .list();
    return List.copyOf(hits);
  }
}
