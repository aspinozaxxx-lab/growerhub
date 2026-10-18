import { Fragment } from 'react';
import { marked } from 'marked';

// HTML, izobrazheniya i ssylki modeli ne sozdayut aktivnyj kontent.
function render(tokens, depth = 0) {
  if (depth > 24) return null;
  return tokens.map((token, index) => {
    const children = () => render(token.tokens || [], depth + 1);
    let node;
    switch (token.type) {
      case 'space': return null;
      case 'paragraph': node = <p>{children()}</p>; break;
      case 'heading': node = <p className="assistant-answer-heading"><strong>{children()}</strong></p>; break;
      case 'strong': node = <strong>{children()}</strong>; break;
      case 'em': node = <em>{children()}</em>; break;
      case 'del': node = <s>{children()}</s>; break;
      case 'codespan': node = <code>{token.text}</code>; break;
      case 'code': node = <pre><code>{token.text}</code></pre>; break;
      case 'br': node = <br />; break;
      case 'hr': node = <hr />; break;
      case 'blockquote': node = <blockquote>{children()}</blockquote>; break;
      case 'list': {
        const items = token.items.map((item, n) => <li key={n}>{render(item.tokens, depth + 1)}</li>);
        node = token.ordered ? <ol start={token.start}>{items}</ol> : <ul>{items}</ul>; break;
      }
      case 'table': node = <div className="assistant-answer-table"><table><thead><tr>{token.header.map((cell, n) => <th key={n}>{render(cell.tokens, depth + 1)}</th>)}</tr></thead><tbody>{token.rows.map((row, n) => <tr key={n}>{row.map((cell, c) => <td key={c}>{render(cell.tokens, depth + 1)}</td>)}</tr>)}</tbody></table></div>; break;
      case 'link': node = <>{children()} ({token.href})</>; break;
      case 'image': node = token.text; break;
      default: node = token.tokens ? children() : token.text || token.raw;
    }
    return <Fragment key={index}>{node}</Fragment>;
  });
}

export default function AssistantText({ text = '' }) {
  return <div className="assistant-answer-text">{render(marked.lexer(text))}</div>;
}
