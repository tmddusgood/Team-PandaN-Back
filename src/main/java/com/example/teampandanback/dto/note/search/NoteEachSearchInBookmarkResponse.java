package com.example.teampandanback.dto.note.search;

import com.example.teampandanback.domain.note.Step;
import lombok.Getter;

@Getter
public class NoteEachSearchInBookmarkResponse {
    private Long noteId;
    private String title;
    private Step step;
    private Long projectId;
    private String projectTitle;

    public NoteEachSearchInBookmarkResponse(Long noteId, String title, Step step, Long projectId, String projectTitle) {
        this.noteId = noteId;
        this.title = title;
        this.step = step;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
    }
}