package es.ucm.fdi.iw.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppStats {
    private long publicRooms;
    private long privateRooms;
    private long totalUsers;
    private long totalSongs;

    public AppStats(long publicRooms, long privateRooms, long totalUsers, long totalSongs){
        this.publicRooms = publicRooms;
        this.privateRooms = privateRooms;
        this.totalUsers = totalUsers;
        this.totalSongs  = totalSongs;
    }

}
