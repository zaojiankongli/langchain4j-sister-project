package com.zjkl.emotion.util;

import java.util.*;

/**
 * AC 自动机多模式匹配器。
 */
public class AhoCorasickMatcher {

    private static class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        TrieNode fail;
        String output;
    }

    private final TrieNode root;
    private TrieNode current;

    public AhoCorasickMatcher(String... keywords) {
        this(new LinkedHashSet<>(Arrays.asList(keywords)));
    }

    public AhoCorasickMatcher(Set<String> keywords) {
        this.root = new TrieNode();
        this.root.fail = this.root;

        for (String keyword : keywords) {
            if (keyword == null || keyword.isEmpty()) continue;
            TrieNode node = root;
            for (char c : keyword.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new TrieNode());
            }
            node.output = keyword;
        }

        Queue<TrieNode> queue = new LinkedList<>();
        for (TrieNode child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            TrieNode u = queue.poll();
            for (Map.Entry<Character, TrieNode> entry : u.children.entrySet()) {
                char c = entry.getKey();
                TrieNode v = entry.getValue();

                TrieNode f = u.fail;
                while (f != root && !f.children.containsKey(c)) {
                    f = f.fail;
                }
                v.fail = f.children.getOrDefault(c, root);

                if (v.output == null && v.fail.output != null) {
                    v.output = v.fail.output;
                }
                queue.add(v);
            }
        }

        this.current = root;
    }

    /**
     * 喂入一个字符，返回命中关键字（小写）或 null。
     * 调用方应先对字符做 {@code Character.toLowerCase(c)} 再传入。
     */
    public String feedChar(char c) {
        while (current != root && !current.children.containsKey(c)) {
            current = current.fail;
        }
        TrieNode next = current.children.get(c);
        current = (next != null) ? next : root;
        return current.output;
    }

    /** 重置自动机到根节点，准备下一轮匹配。 */
    public void reset() {
        current = root;
    }
}
