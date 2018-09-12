package no.uio.ifi.trackfind.backend.repositories;

import no.uio.ifi.trackfind.backend.dao.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    long countByRepository(String repository);

    Collection<Dataset> findAllByVersion(long version);

    Collection<Dataset> findAllByIdIn(Collection<Long> ids);

    Dataset findByIdAndVersion(long id, long version);

}
