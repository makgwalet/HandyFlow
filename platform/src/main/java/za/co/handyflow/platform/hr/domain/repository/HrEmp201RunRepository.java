package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmp201Run;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HrEmp201RunRepository extends JpaRepository<HrEmp201Run, UUID> {

    @Query("SELECT l FROM HrEmp201Run l WHERE l.emp201Id = :emp201Id")
    List<HrEmp201Run> forEmp201(UUID emp201Id);

    @Query("SELECT l FROM HrEmp201Run l WHERE l.emp201Id IN :emp201Ids")
    List<HrEmp201Run> forEmp201s(Collection<UUID> emp201Ids);
}
