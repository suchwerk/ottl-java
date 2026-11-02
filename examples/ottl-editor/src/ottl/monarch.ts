/**
 * Monaco Monarch tokenizer and language configuration for OTTL.
 *
 * Highlighting only — all semantics (diagnostics, completion, hover, signatures) come from the
 * Java language server over the WebSocket. We deliberately do not reimplement the language here.
 */
import type * as monaco from '@codingame/monaco-vscode-editor-api';

export const OTTL_LANGUAGE_ID = 'ottl';

export const ottlLanguageConfiguration: monaco.languages.LanguageConfiguration = {
    comments: { lineComment: '#' },
    brackets: [
        ['(', ')'],
        ['[', ']'],
        ['{', '}']
    ],
    autoClosingPairs: [
        { open: '(', close: ')' },
        { open: '[', close: ']' },
        { open: '{', close: '}' },
        { open: '"', close: '"' }
    ],
    surroundingPairs: [
        { open: '(', close: ')' },
        { open: '[', close: ']' },
        { open: '{', close: '}' },
        { open: '"', close: '"' }
    ],
    indentationRules: {
        increaseIndentPattern: /\{[^}"']*$/,
        decreaseIndentPattern: /^\s*\}/
    }
};

export const ottlMonarchTokens: monaco.languages.IMonarchLanguage = {
    defaultToken: '',
    ignoreCase: false,
    keywords: ['where', 'and', 'or', 'not', 'if', 'else'],
    constants: ['true', 'false', 'nil'],
    operators: ['==', '!=', '>', '>=', '<', '<=', '='],
    symbols: /[=><!]+/,
    tokenizer: {
        root: [
            // the `# context: Order` directive and ordinary comments
            [/#.*$/, 'comment'],

            // function call: identifier immediately followed by '('
            [/[a-zA-Z_]\w*(?=\s*\()/, 'entity.name.function'],

            // identifiers, keywords and constants
            [/[a-zA-Z_]\w*/, {
                cases: {
                    '@keywords': 'keyword',
                    '@constants': 'constant',
                    '@default': 'identifier'
                }
            }],

            // property access dot
            [/\./, 'delimiter'],

            // numbers
            [/\d+\.\d+([eE][-+]?\d+)?/, 'number.float'],
            [/\d+/, 'number'],

            // strings
            [/"/, { token: 'string.quote', next: '@string' }],

            // operators / punctuation
            [/@symbols/, {
                cases: {
                    '@operators': 'operator',
                    '@default': ''
                }
            }],
            [/[(),\[\]{}]/, 'delimiter'],

            [/\s+/, 'white']
        ],
        string: [
            [/[^"\\]+/, 'string'],
            [/\\./, 'string.escape'],
            [/"/, { token: 'string.quote', next: '@pop' }]
        ]
    }
};
