package com.ittools.platform.service;

import com.ittools.platform.service.dto.ImportResult;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs student/teacher imports in the background so the admin page can poll
 * live progress instead of waiting on one long request. Jobs run one at a
 * time (imports check uniqueness against the DB, so they must not overlap)
 * and are kept in memory for an hour after they finish.
 */
@Service
public class ImportJobService {

    public enum Kind { STUDENTS, TEACHERS }
    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    /** Live state of one import; serialized to JSON via bean getters. */
    public static final class Job {
        private final String id;
        private final Kind kind;
        private final String filename;
        private volatile Status status = Status.QUEUED;
        private volatile int total;
        private volatile int processed;
        private volatile int imported;
        private volatile int failed;
        private volatile String message;
        private volatile ImportResult result;
        private volatile long finishedAt;

        Job(Kind kind, String filename) {
            this.id = UUID.randomUUID().toString();
            this.kind = kind;
            this.filename = filename;
        }

        public String getId() { return id; }
        public Kind getKind() { return kind; }
        public String getFilename() { return filename; }
        public Status getStatus() { return status; }
        public int getTotal() { return total; }
        public int getProcessed() { return processed; }
        public int getImported() { return imported; }
        public int getFailed() { return failed; }
        public String getMessage() { return message; }
        /** Full per-row result; only set once the job is DONE. */
        public ImportResult getResult() { return result; }
    }

    private static final long KEEP_MS = 60 * 60 * 1000L;

    private final ImportService importService;
    private final Map<String, Job> jobs = new ConcurrentHashMap<String, Job>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "import-worker");
        t.setDaemon(true);
        return t;
    });

    public ImportJobService(ImportService importService) {
        this.importService = importService;
    }

    public Job start(Kind kind, byte[] content, String filename) {
        prune();
        Job job = new Job(kind, filename);
        jobs.put(job.id, job);
        worker.submit(() -> run(job, content));
        return job;
    }

    public Job get(String id) {
        Job job = jobs.get(id);
        if (job == null) throw new IllegalArgumentException("导入任务不存在或已过期");
        return job;
    }

    private void run(Job job, byte[] content) {
        job.status = Status.RUNNING;
        ImportService.Progress progress = (processed, total, imported, failed) -> {
            job.total = total;
            job.processed = processed;
            job.imported = imported;
            job.failed = failed;
        };
        try {
            ImportResult result = job.kind == Kind.STUDENTS
                    ? importService.importStudents(new ByteArrayInputStream(content), job.filename, progress)
                    : importService.importTeachers(new ByteArrayInputStream(content), job.filename, progress);
            job.total = result.getTotal();
            job.processed = result.getTotal();
            job.imported = result.getImported();
            job.failed = result.getFailed();
            job.result = result;
            job.status = Status.DONE;
        } catch (Exception e) {
            // the import runs in one transaction, so a failure here means nothing was saved
            job.imported = 0;
            job.message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.status = Status.FAILED;
        } finally {
            job.finishedAt = System.currentTimeMillis();
        }
    }

    private void prune() {
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        for (Iterator<Job> it = jobs.values().iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (j.finishedAt != 0 && j.finishedAt < cutoff) it.remove();
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
