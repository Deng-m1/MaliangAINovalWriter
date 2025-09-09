package com.ainovel.server.service.impl;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.ainovel.server.domain.dto.ParsedNovelData;
import com.ainovel.server.domain.dto.ParsedSceneData;
import com.ainovel.server.service.NovelParser;

import lombok.extern.slf4j.Slf4j;

/**
 * TXT格式小说解析器实现
 */
@Slf4j
@Component
public class TxtNovelParser implements NovelParser {

    /**
     * 章节标题模式 匹配： 1. 第[数字/中文数字][章节部回] 标题 - 中文模式 2. Chapter [数字] 标题 - 英文模式 3.
     * 罗马数字章节 4. 增加了更多常见的分章格式，包括"正文/番外"系列格式
     */
    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile(
            "^\\s*(?:(?:(?:正文|番外)(?:\\s+)?(?:第[一二三四五六七八九十百千万零〇\\d]+章)?)|(?:序章|楔子|尾声|后记|(?:第[一二三四五六七八九十百千万零〇\\d]+[章卷节部回集]))|(?:[\\(（【]?\\s*[一二三四五六七八九十百千万零〇\\d]+\\s*[\\)）】]?[\\s.、：:])|(?:Chapter\\s+\\d+)|(?:[IVXLCDM]+))[\\s.、.:：]*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    // 备用章节识别模式，当内容行超过特定长度时判断是否是新章节的开始
    private static final Pattern BACKUP_CHAPTER_PATTERN = Pattern.compile(
            "^\\s*(.{1,30})(?:[\\s.、.:：]+|$)",
            Pattern.CASE_INSENSITIVE
    );

    // 通用章节识别：匹配任意前缀后跟"第N章"形式
    private static final Pattern GENERIC_CHAPTER_PATTERN = Pattern.compile(
            "^\\s*.{0,20}?第[一二三四五六七八九十百千万零〇\\d]+章.{0,50}$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public ParsedNovelData parseStream(Stream<String> lines) {
        ParsedNovelData parsedNovelData = new ParsedNovelData();
        parsedNovelData.setNovelTitle("导入的小说"); // 默认标题，可以从文件名推断

        AtomicReference<String> currentChapterTitle = new AtomicReference<>("");
        StringBuilder currentContent = new StringBuilder();
        AtomicInteger chapterCount = new AtomicInteger(0);
        AtomicInteger lineCount = new AtomicInteger(0);
        AtomicInteger emptyLineCount = new AtomicInteger(0);
        AtomicInteger consecutiveEmptyLineCount = new AtomicInteger(0); // 记录连续空行数
        AtomicReference<String> lastNonEmptyLine = new AtomicReference<>(""); // 记录上一个非空行

        // 使用reduce操作处理流
        lines.forEach(line -> {
            lineCount.incrementAndGet();
            String trimmedLine = line.trim();
            boolean isEmpty = trimmedLine.isEmpty();

            if (isEmpty) {
                emptyLineCount.incrementAndGet();
                consecutiveEmptyLineCount.incrementAndGet(); // 增加连续空行计数
                
                // 空行仍需添加到内容中
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                return;
            } else {
                // 按优先级 1) 正则章节标题 2) 通用"第N章"识别逻辑 3) 备用章节检测

                // 1) 正则章节标题检测
                Matcher matcher = CHAPTER_TITLE_PATTERN.matcher(trimmedLine);

                // 2) 通用"第N章"识别逻辑
                boolean isGenericMatch = false;
                if (!matcher.matches() && GENERIC_CHAPTER_PATTERN.matcher(trimmedLine).matches()) {
                    isGenericMatch = true;
                    log.debug("使用通用章节识别: '{}'", trimmedLine);
                }

                // 3) 备用章节识别逻辑：仅在未匹配以上两种时触发，基于空行与长度判断
                boolean isBackupChapterDetected = false;
                if (!matcher.matches() && !isGenericMatch &&
                        (emptyLineCount.get() >= 2 || consecutiveEmptyLineCount.get() >= 2) &&
                        trimmedLine.length() < 50) {
                    Matcher backupMatcher = BACKUP_CHAPTER_PATTERN.matcher(trimmedLine);
                    if (backupMatcher.matches() && !isContentParagraph(trimmedLine)) {
                        isBackupChapterDetected = true;
                        log.debug("使用备用章节识别: '{}'", trimmedLine);
                    }
                }

                boolean handledByTitleDetection = false;

                if (matcher.matches() || isGenericMatch || isBackupChapterDetected) {
                    // 如果当前有内容，则保存上一章节
                    if (currentContent.length() > 0) {
                        saveCurrentChapter(parsedNovelData, currentChapterTitle.get(),
                                currentContent.toString(), chapterCount.get());
                        currentContent.setLength(0); // 清空内容缓冲
                    }

                    // 计算新的章节序号
                    int newChapterNum = chapterCount.incrementAndGet();

                    // 提取章节标题
                    String titleText;
                    if (matcher.matches()) {
                        titleText = matcher.group(1);
                        if (titleText == null || titleText.trim().isEmpty()) {
                            titleText = "第" + newChapterNum + "章";
                        } else {
                            titleText = titleText.trim();
                        }
                        currentChapterTitle.set(trimmedLine);
                        log.debug("通过正则表达式识别到章节标题: {}", trimmedLine);
                    } else if (isGenericMatch) {
                        titleText = trimmedLine;
                        currentChapterTitle.set(trimmedLine);
                        log.debug("通过通用方式识别到章节标题: {}", trimmedLine);
                    } else {
                        // 使用备用识别的标题
                        titleText = trimmedLine;
                        currentChapterTitle.set(trimmedLine);
                        log.debug("通过备用方式识别到章节标题: {}", trimmedLine);
                    }

                    log.debug("识别到章节标题[{}]: {}", newChapterNum, currentChapterTitle.get());

                    handledByTitleDetection = true;
                } else {
                    // 尚未识别章节标题，后续可能基于空行分章
                }

                // 3) 基于连续空行分章逻辑 - 仅当未通过标题检测切分章节时执行
                if (!handledByTitleDetection) {
                    boolean shouldSplitByEmptyLines = consecutiveEmptyLineCount.get() >= 2 &&
                            currentContent.length() > 0 &&
                            chapterCount.get() > 0; // 确保不是第一章开始

                    if (shouldSplitByEmptyLines) {
                        log.debug("基于连续空行分章: 发现{}个连续空行", consecutiveEmptyLineCount.get());

                        if (currentContent.length() > 0) {
                            saveCurrentChapter(parsedNovelData, currentChapterTitle.get(),
                                    currentContent.toString(), chapterCount.get());
                            currentContent.setLength(0);
                        }

                        int nextChapterNum = chapterCount.incrementAndGet();
                        String newTitle = "第" + nextChapterNum + "章";
                        currentChapterTitle.set(newTitle);
                        log.debug("基于连续空行创建新章节[{}]: {}", nextChapterNum, newTitle);
                        // 空行分章后立即继续内容合并，不将当前行添加为正文内容，后续循环会处理
                        // 重置计数器
                        consecutiveEmptyLineCount.set(0);
                        emptyLineCount.set(0);
                        // 继续到下一循环
                        return;
                    }
                }

                // 重置连续空行计数器
                consecutiveEmptyLineCount.set(0);
                emptyLineCount.set(0);

                // 内容行，添加到当前内容
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(trimmedLine); // 去除尾部空白

                // 保存当前行作为最近的非空行
                lastNonEmptyLine.set(trimmedLine);

                // 如果是第一行但不是章节标题，可能需要创建默认第一章
                if (lineCount.get() <= 3 && chapterCount.get() == 0 && currentChapterTitle.get().isEmpty()) {
                    currentChapterTitle.set("第1章");
                    chapterCount.incrementAndGet();
                    log.debug("创建默认第一章");
                }
            }
        });

        // 处理最后一章
        if (currentContent.length() > 0) {
            // 如果没有识别到任何章节标题，但有内容，创建一个默认的第一章
            if (chapterCount.get() == 0) {
                currentChapterTitle.set("第1章");
                chapterCount.incrementAndGet();
                log.debug("创建默认唯一章节");
            }

            saveCurrentChapter(parsedNovelData, currentChapterTitle.get(),
                    currentContent.toString(), chapterCount.get() - 1);
        }

        log.info("TXT解析完成，共解析出{}个章节", parsedNovelData.getScenes().size());
        return parsedNovelData;
    }

    /**
     * 判断是否是正常内容段落，而不是章节标题 通常段落都比较长，且包含标点符号
     */
    private boolean isContentParagraph(String line) {
        // 如果长度大于50，很可能是内容段落而非标题
        if (line.length() > 50) {
            return true;
        }

        // 检查是否包含常见的段落标点
        Pattern punctPattern = Pattern.compile("[，。！？；,.!?;]");
        return punctPattern.matcher(line).find() && line.length() > 20;
    }

    private void saveCurrentChapter(ParsedNovelData parsedNovelData, String title, String content, int order) {
        // 如果是第一章并且没有标题，可能是前言或引言
        if (order == 0 && (title == null || title.isEmpty())) {
            title = "前言";
        }

        // 如果仍然没有标题，使用默认章节标题
        if (title == null || title.isEmpty()) {
            title = "第" + (order + 1) + "章";
        }

        ParsedSceneData sceneData = ParsedSceneData.builder()
                .sceneTitle(title)
                .sceneContent(normalizeContent(content))
                .order(order)
                .build();

        parsedNovelData.addScene(sceneData);
        log.debug("保存章节[{}]: {}, 内容长度: {}", order, title, content.length());
    }

    @Override
    public String getSupportedExtension() {
        return "txt";
    }

    /**
     * 归一化内容，移除多余空行（>1 连续空行压缩为 1 行）
     */
    private String normalizeContent(String rawContent) {
        if (rawContent == null) {
            return "";
        }
        // 使用正则将多余空行压缩为单个空行
        return rawContent.replaceAll("(?m)(?:^[ \t]*\n){2,}", "\n");
    }
}
