package com.omr.controller;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.omr.model.Quiz;
import com.omr.repository.QuizRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/** Servlet REST endpoints for quiz and answer-key management. */
@WebServlet("/api/quizzes/*")
public class QuizController extends HttpServlet {
    private static final Gson JSON = new Gson();
    private static final Type KEY_TYPE = new TypeToken<Map<Integer,String>>(){}.getType();
    private QuizRepository repository;
    @Override public void init() throws ServletException { repository = new QuizRepository(); }

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        String path=req.getPathInfo();
        if(path==null || path.equals("/")) { json(resp,200,repository.findAll()); return; }
        String[] parts=parts(path);
        Optional<Quiz> q=repository.findById(parts[0]);
        if(q.isEmpty()) { json(resp,404,Map.of("error","Quiz not found")); return; }

        if(parts.length==2 && ("export-csv".equals(parts[1]) || "csv".equals(parts[1]))) {
            com.omr.repository.SubmissionRepository subs = new com.omr.repository.SubmissionRepository();
            String csvData = subs.generateCsv(q.get().getId(), q.get());
            String safeTitle = q.get().getTitle() != null ? q.get().getTitle().replaceAll("[^a-zA-Z0-9_-]", "_") : "quiz";
            String filename = safeTitle + "_" + q.get().getId() + "_scores.csv";

            resp.setStatus(200);
            resp.setContentType("text/csv; charset=UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            resp.getOutputStream().write(csvData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return;
        }

        json(resp,200,q.get());
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        JsonObject body=readObject(req); Quiz q=JSON.fromJson(body,Quiz.class);
        if(q.getTitle()==null||q.getTitle().isBlank())q.setTitle("Untitled Quiz");
        if(q.getNumberOfQuestions()<=0||q.getNumberOfQuestions()>15) { json(resp,400,Map.of("error","This answer sheet supports 1 to 15 questions"));return; }
        if(q.getMaximumMarks()<=0) q.setMaximumMarks(10.0);
        if(!Double.isFinite(q.getMaximumMarks())||q.getMaximumMarks()>1000) { json(resp,400,Map.of("error","Maximum marks must be greater than 0 and no more than 1000"));return; }
        q.setId(UUID.randomUUID().toString().substring(0,8)); q.setAnswerKey(new HashMap<>());
        json(resp,201,repository.save(q));
    }
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        String[] parts=parts(req.getPathInfo());
        if(parts.length!=2||!parts[1].equals("answer-key")){json(resp,404,Map.of("error","Unknown endpoint"));return;}
        Optional<Quiz> found=repository.findById(parts[0]); if(found.isEmpty()){json(resp,404,Map.of("error","Quiz not found"));return;}
        JsonObject body=readObject(req); if(!body.has("answers")||!body.get("answers").isJsonObject()){json(resp,400,Map.of("error","Answer key must contain an answers object"));return;}
        Map<Integer,String> answers=JSON.fromJson(body.get("answers"),KEY_TYPE);
        for(var e:answers.entrySet()) { int n=e.getKey(); String option=e.getValue()==null?"":e.getValue().toUpperCase(Locale.ROOT);
            if(n<1||n>found.get().getNumberOfQuestions()){json(resp,400,Map.of("error","Invalid question number: "+n));return;}
            if(!option.matches("[A-D]")){json(resp,400,Map.of("error","Invalid option for Q"+n+"; must be A, B, C, or D"));return;} e.setValue(option);
        }
        Quiz q=found.get();q.setAnswerKey(answers);json(resp,200,repository.save(q));
    }
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        String path=req.getPathInfo(); if(path==null||path.equals("/")){json(resp,400,Map.of("error","Quiz ID required"));return;}
        if(!repository.deleteById(segment(path,1))){json(resp,404,Map.of("error","Quiz not found"));return;}resp.setStatus(204);
    }
    private static JsonObject readObject(HttpServletRequest req)throws IOException { JsonElement e=JsonParser.parseReader(req.getReader()); if(!e.isJsonObject())throw new JsonParseException("Expected JSON object");return e.getAsJsonObject(); }
    private static String segment(String path,int index){return parts(path)[index-1];}
    private static String[] parts(String path){return Arrays.stream((path==null?"":path).split("/")).filter(s->!s.isBlank()).toArray(String[]::new);}
    private static void json(HttpServletResponse r,int status,Object o)throws IOException {r.setStatus(status);r.setContentType("application/json;charset=UTF-8");JSON.toJson(o,r.getWriter());}
}
