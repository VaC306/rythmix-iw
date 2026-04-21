package es.ucm.fdi.iw.auxiliar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.ucm.fdi.iw.model.AuditWeb;
import es.ucm.fdi.iw.model.User;
import es.ucm.fdi.iw.repository.AuditWebRepository;

@Component
public class AuditHelper {
    @Autowired
    private AuditWebRepository auditWebRepository;

    public void log(User user, String actionPerformed, String moreDetails){
        AuditWeb aw = new AuditWeb();
        aw.setUser(user);
        aw.setActionPerformed(actionPerformed);
        aw.setMoreDetails(moreDetails);
        auditWebRepository.save(aw);
    }
}
