package com.example.teampandanback.service;

import com.example.teampandanback.domain.Comment.CommentRepository;
import com.example.teampandanback.domain.bookmark.Bookmark;
import com.example.teampandanback.domain.bookmark.BookmarkRepository;
import com.example.teampandanback.domain.note.Note;
import com.example.teampandanback.domain.note.NoteRepository;
import com.example.teampandanback.domain.note.Step;
import com.example.teampandanback.domain.project.Project;
import com.example.teampandanback.domain.project.ProjectRepository;
import com.example.teampandanback.domain.user.User;
import com.example.teampandanback.domain.user_project_mapping.UserProjectMapping;
import com.example.teampandanback.domain.user_project_mapping.UserProjectMappingRepository;
import com.example.teampandanback.dto.auth.SessionUser;
import com.example.teampandanback.dto.note.request.NoteCreateRequestDto;
import com.example.teampandanback.dto.note.request.NoteUpdateRequestDto;
import com.example.teampandanback.dto.note.response.*;
import com.example.teampandanback.exception.ApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class NoteService {
    private final NoteRepository noteRepository;
    private final UserProjectMappingRepository userProjectMappingRepository;
    private final ProjectRepository projectRepository;
    private final BookmarkRepository bookmarkRepository;
    private final CommentRepository commentRepository;

    // String 자료형으로 받은 날짜를 LocalDate 자료형으로 형변환
    private LocalDate changeType(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.parse(dateString, formatter);
        return date;
    }

    // Note 상세 조회
    @Transactional
    public NoteResponseDto readNoteDetail(Long noteId, SessionUser sessionUser) {
        NoteResponseDto noteResponseDto = noteRepository.findByNoteId(noteId)
                .orElseThrow(() -> new ApiRequestException("작성된 노트가 없습니다."));

        Optional<Bookmark> bookmark = bookmarkRepository.findByUserIdAndNoteId(sessionUser.getUserId(), noteId);
        noteResponseDto.setBookmark(bookmark.isPresent());
        return noteResponseDto;
    }

    // Note 업데이트
    @Transactional
    public NoteUpdateResponseDto updateNoteDetail(Long noteId, NoteUpdateRequestDto noteUpdateRequestDto) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ApiRequestException("수정 할 노트가 없습니다."));

        note.update(noteUpdateRequestDto, changeType(noteUpdateRequestDto.getDeadline()), Step.valueOf(noteUpdateRequestDto.getStep()));

        return NoteUpdateResponseDto.of(note);
    }

    // Note 작성
    @Transactional
    public NoteCreateResponseDto createNote(Long projectId, NoteCreateRequestDto noteCreateRequestDto, SessionUser sessionUser) {

        Optional<UserProjectMapping> userProjectMapping =
                userProjectMappingRepository
                        .findByUserIdAndProjectId(sessionUser.getUserId(), projectId);
        System.out.println(userProjectMapping.get().getProject().getClass());
        System.out.println(userProjectMapping.get().getUser().getClass());


        if(!userProjectMapping.isPresent()){
            throw new ApiRequestException("해당 유저가 해당 프로젝트에 참여해있지 않습니다.");
        }

        // [노트 생성] 전달받은 String deadline을 LocalDate 자료형으로 형변환
        LocalDate deadline = changeType(noteCreateRequestDto.getDeadline());

        // [노트 생성] 전달받은 String step을 Enum Step으로
        Step step = Step.valueOf(noteCreateRequestDto.getStep());

        // [노트 생성] 찾은 userProjectMappingRepository를 통해 user와 프로젝트 가져오기
        User user = userProjectMapping.get().getUser();
        Project project = userProjectMapping.get().getProject();

        // [노트 생성] 전달받은 noteCreateRequestDto를 Note.java에 정의한 of 메소드에 전달하여 빌더 패턴에 넣는다.
        Note note = noteRepository.save(Note.of(noteCreateRequestDto, deadline, step, user, project));
        return NoteCreateResponseDto.of(note);
    }

    // 해당 Project 에서 내가 작성한 Note 조회
    public NoteMineInProjectResponseDto readNotesMineOnly(Long projectId, SessionUser sessionUser) {

        // Project 조회
        projectRepository.findById(projectId).orElseThrow(
                () -> new ApiRequestException("내가 작성한 문서를 조회할 프로젝트가 없습니다.")
        );

        // 해당 Project 에서 내가 작성한 Note 죄회
        List<NoteReadMineEachResponseDto> myNoteList = noteRepository.findAllNoteByProjectAndUserOrderByCreatedAtDesc(projectId, sessionUser.getUserId())
                .stream()
                .map(NoteReadMineEachResponseDto::fromEntity)
                .collect(Collectors.toList());

        return NoteMineInProjectResponseDto.of(myNoteList);
    }

    // 전체 Project 에서 내가 북마크한 Note 조회
    public NoteBookmarkedResponseDto readBookmarkedMine(SessionUser sessionUser) {

        // 해당 북마크한 Note 조회
        List<NoteEachBookmarkedResponseDto> noteEachBookmarkedResponseDto =
                bookmarkRepository.findByUserId(sessionUser.getUserId());

        return NoteBookmarkedResponseDto.builder().noteList(noteEachBookmarkedResponseDto).build();
    }

    // Note 삭제
    @Transactional
    public NoteDeleteResponseDto deleteNote(Long noteId) {
        // 삭제할 Note 조회
        Note note = noteRepository.findById(noteId).orElseThrow(
                () -> new ApiRequestException("이미 삭제된 노트입니다.")
        );

        // Note에 연관된  코멘트 삭제
        commentRepository.deleteCommentByNoteId(noteId);

        // Note 에 연관된 북마크 삭제
        bookmarkRepository.deleteByNote(noteId);

        // Note 삭제
        noteRepository.delete(note);

        return NoteDeleteResponseDto.builder()
                .noteId(noteId)
                .build();
    }

    // Note 칸반형 조회 (칸반 페이지)
    @Transactional
    public KanbanNoteSearchResponseDto readKanbanNote(Long projectId) {
        List<NoteOfProjectResponseDto> noteOfProjectResponseDtoList = new ArrayList<>();
        List<KanbanNoteEachResponseDto> kanbanNoteEachResponseDtoList1 = new ArrayList<>();
        List<KanbanNoteEachResponseDto> kanbanNoteEachResponseDtoList2 = new ArrayList<>();
        List<KanbanNoteEachResponseDto> kanbanNoteEachResponseDtoList3 = new ArrayList<>();
        List<KanbanNoteEachResponseDto> kanbanNoteEachResponseDtoList4 = new ArrayList<>();

        // Project 조회
        Project project = projectRepository.findById(projectId).orElseThrow(
                ()-> new ApiRequestException("칸반을 조회할 프로젝트가 없습니다.")
        );

        for (Note note : noteRepository.findByProject(project)) {
            switch(note.getStep()){
                case STORAGE:
                    kanbanNoteEachResponseDtoList1.add((KanbanNoteEachResponseDto.of(note))); break;
                case TODO:
                    kanbanNoteEachResponseDtoList2.add((KanbanNoteEachResponseDto.of(note))); break;
                case PROCESSING:
                    kanbanNoteEachResponseDtoList3.add((KanbanNoteEachResponseDto.of(note))); break;
                case DONE:
                    kanbanNoteEachResponseDtoList4.add(KanbanNoteEachResponseDto.of(note)); break;
            }
        }
        // Note 를 각 상태별로 List 로 묶어서 응답 보내기
        noteOfProjectResponseDtoList.add(NoteOfProjectResponseDto.of(Step.STORAGE, kanbanNoteEachResponseDtoList1));
        noteOfProjectResponseDtoList.add(NoteOfProjectResponseDto.of(Step.TODO, kanbanNoteEachResponseDtoList2));
        noteOfProjectResponseDtoList.add(NoteOfProjectResponseDto.of(Step.PROCESSING, kanbanNoteEachResponseDtoList3));
        noteOfProjectResponseDtoList.add(NoteOfProjectResponseDto.of(Step.DONE, kanbanNoteEachResponseDtoList4));

        return KanbanNoteSearchResponseDto.builder()
                .noteOfProjectResponseDtoList(noteOfProjectResponseDtoList)
                .build();
    }

    // Note 일반형 조회 (파일 페이지)
    @Transactional
    public NoteSearchResponseDto readOrdinaryNote(Long projectId) {
        List<OrdinaryNoteEachResponseDto> ordinaryNoteEachResponseDtoList = new ArrayList<>();

        // Project 조회
        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ApiRequestException("파일을 조회할 프로젝트가 없습니다.")
        );


        for (Note note : noteRepository.findAllByProjectOrderByCreatedAtDesc(project)) {
            ordinaryNoteEachResponseDtoList.add((OrdinaryNoteEachResponseDto.fromEntity(note)));
        }

        return NoteSearchResponseDto.of(ordinaryNoteEachResponseDtoList);
    }

    // 전체 프로젝트에서 내가 작성한 노트 조회
    public List<NoteEachMineInTotalResponseDto> readMyNoteInTotalProject(SessionUser sessionUser) {
        return noteRepository.findUserNoteInTotalProject(sessionUser.getUserId());
    }
}
