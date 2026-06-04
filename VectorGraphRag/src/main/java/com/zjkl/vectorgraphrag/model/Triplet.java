package com.zjkl.vectorgraphrag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Triplet {
    private String subject;
    private String predicate;
    private String object;

    public String toRelationText() {
        return subject + " " + predicate + " " + object;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Triplet triplet)) return false;
        return Objects.equals(subject != null ? subject.toLowerCase() : null,
                              triplet.subject != null ? triplet.subject.toLowerCase() : null)
            && Objects.equals(predicate != null ? predicate.toLowerCase() : null,
                              triplet.predicate != null ? triplet.predicate.toLowerCase() : null)
            && Objects.equals(object != null ? object.toLowerCase() : null,
                              triplet.object != null ? triplet.object.toLowerCase() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                subject != null ? subject.toLowerCase() : null,
                predicate != null ? predicate.toLowerCase() : null,
                object != null ? object.toLowerCase() : null
        );
    }
}
