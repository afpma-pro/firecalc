/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
import React from 'react';

interface MarkdownTextProps {
  text: string;
  className?: string;
}

export default function MarkdownText({ text, className = '' }: MarkdownTextProps) {
  const parseMarkdown = (content: string): React.ReactNode[] => {
    const parts: React.ReactNode[] = [];
    let currentIndex = 0;
    let key = 0;

    // Split by newlines to handle line breaks
    const lines = content.split('\n');

    lines.forEach((line, lineIndex) => {
      let remaining = line;
      const lineParts: React.ReactNode[] = [];
      let partKey = 0;

      while (remaining.length > 0) {
        // Match bold: **text**
        const boldMatch = remaining.match(/^\*\*(.+?)\*\*/);
        if (boldMatch) {
          lineParts.push(
            <strong key={`${key}-${partKey++}`} className="font-bold">
              {boldMatch[1]}
            </strong>
          );
          remaining = remaining.slice(boldMatch[0].length);
          continue;
        }

        // Match italic: *text* (but not **)
        const italicMatch = remaining.match(/^\*([^*]+?)\*/);
        if (italicMatch) {
          lineParts.push(
            <em key={`${key}-${partKey++}`} className="italic">
              {italicMatch[1]}
            </em>
          );
          remaining = remaining.slice(italicMatch[0].length);
          continue;
        }

        // Match links: [text](url)
        const linkMatch = remaining.match(/^\[(.+?)\]\((.+?)\)/);
        if (linkMatch) {
          lineParts.push(
            <a
              key={`${key}-${partKey++}`}
              href={linkMatch[2]}
              target="_blank"
              rel="noopener noreferrer"
              className="underline hover:text-firecalc-yellow transition-colors"
            >
              {linkMatch[1]}
            </a>
          );
          remaining = remaining.slice(linkMatch[0].length);
          continue;
        }

        // Regular text until next special character
        const nextSpecial = remaining.search(/[\*\[]/);
        if (nextSpecial === -1) {
          lineParts.push(<span key={`${key}-${partKey++}`}>{remaining}</span>);
          remaining = '';
        } else {
          lineParts.push(
            <span key={`${key}-${partKey++}`}>{remaining.slice(0, nextSpecial)}</span>
          );
          remaining = remaining.slice(nextSpecial);
        }
      }

      // Add line content
      if (lineParts.length > 0) {
        parts.push(<span key={key++}>{lineParts}</span>);
      }

      // Add line break if not the last line
      if (lineIndex < lines.length - 1) {
        parts.push(<br key={`br-${key++}`} />);
      }
    });

    return parts;
  };

  return <span className={className}>{parseMarkdown(text)}</span>;
}