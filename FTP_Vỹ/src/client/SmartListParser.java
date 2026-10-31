package client;

import common.FtpFileEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trình phân tích danh sách tập tin thông minh (Smart LIST Parser)
 * Tương thích cả định dạng Unix (RFC 959 / FileZilla) và DOS Windows IIS (Kịch bản 2 & 4)
 */
public class SmartListParser {

    // Regex Unix format: drwxr-xr-x 1 owner group 1024 Oct 24 07:00 filename
    private static final Pattern UNIX_PATTERN = Pattern.compile(
            "^([bcdl\\-][rwx\\-]{9})\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+([A-Za-z]{3}\\s+\\d{1,2}\\s+[\\d:]{4,5})\\s+(.+)$"
    );

    // Regex Windows IIS DOS format: 10-24-26  07:00AM  <DIR>  documents OR 10-24-26 07:00AM 1024 report.pdf
    private static final Pattern DOS_PATTERN = Pattern.compile(
            "^(\\d{2}-\\d{2}-\\d{2,4}\\s+\\d{2}:\\d{2}(?:[AP]M)?)\\s+(<DIR>|\\d+)\\s+(.+)$"
    );

    public static List<FtpFileEntry> parseListing(List<String> rawLines) {
        List<FtpFileEntry> entries = new ArrayList<>();
        if (rawLines == null) return entries;

        for (String line : rawLines) {
            if (line == null || line.trim().isEmpty()) continue;
            FtpFileEntry entry = parseLine(line.trim());
            if (entry != null) {
                // Loại trừ . và .. để giao diện quản lý chủ động
                if (!entry.getName().equals(".") && !entry.getName().equals("..")) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public static FtpFileEntry parseLine(String line) {
        // 1. Thử phân tích theo chuẩn Unix
        Matcher unixMatcher = UNIX_PATTERN.matcher(line);
        if (unixMatcher.matches()) {
            String perms = unixMatcher.group(1);
            boolean isDir = perms.startsWith("d");
            long size = Long.parseLong(unixMatcher.group(2));
            String name = unixMatcher.group(4);
            long time = parseUnixDate(unixMatcher.group(3));
            return new FtpFileEntry(name, size, isDir, perms, time);
        }

        // 2. Thử phân tích theo chuẩn DOS của Windows IIS
        Matcher dosMatcher = DOS_PATTERN.matcher(line);
        if (dosMatcher.matches()) {
            String dateStr = dosMatcher.group(1);
            String dirOrSize = dosMatcher.group(2);
            String name = dosMatcher.group(3);
            boolean isDir = dirOrSize.equalsIgnoreCase("<DIR>");
            long size = isDir ? 0 : Long.parseLong(dirOrSize);
            long time = parseDosDate(dateStr);
            return new FtpFileEntry(name, size, isDir, isDir ? "drwxr-xr-x" : "-rw-r--r--", time);
        }

        // 3. Fallback: Nếu là dòng text đơn giản
        String[] tokens = line.split("\\s+");
        String name = tokens[tokens.length - 1];
        return new FtpFileEntry(name, 0, false, "-rw-r--r--", System.currentTimeMillis());
    }

    private static long parseUnixDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
            Date d = sdf.parse(dateStr);
            return d.getTime();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private static long parseDosDate(String dateStr) {
        String cleanDate = dateStr.trim();
        String[] patterns = {
            "MM-dd-yyyy hh:mma",
            "MM-dd-yy hh:mma",
            "MM-dd-yyyy  hh:mma",
            "MM-dd-yy  hh:mma",
            "yyyy-MM-dd HH:mm"
        };
        for (String pat : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pat, Locale.ENGLISH);
                sdf.setLenient(true);
                Date d = sdf.parse(cleanDate);
                return d.getTime();
            } catch (Exception ignored) {}
        }
        return System.currentTimeMillis();
    }
}
