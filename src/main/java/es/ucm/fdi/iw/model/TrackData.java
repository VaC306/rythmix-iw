package es.ucm.fdi.iw.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackData {
    private int instrument;
    private List<MIDITrack.Note> notes;
}
