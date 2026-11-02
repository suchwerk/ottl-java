/** Typed client for the development-only editor REST API exposed by the Quarkus example. */

export interface PropertyDto {
    name: string;
    type: string;
    readable: boolean;
    writable: boolean;
}

export interface ContextDto {
    name: string;
    variable: string;
    properties: PropertyDto[];
}

export interface FunctionDto {
    name: string;
    signature: string;
    documentation: string;
    category: string;
    minArgs: number;
    maxArgs: number;
}

export interface MetadataDto {
    contexts: ContextDto[];
    functions: FunctionDto[];
}

export interface ValidateResponse {
    valid: boolean;
    error: string | null;
}

export interface RuleDto {
    context: string;
    ruleId: string;
    source: string;
    version: number;
}

async function asJson<T>(res: Response): Promise<T> {
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
        const message = (body && (body.error as string)) || `Request failed (${res.status})`;
        throw new Error(message);
    }
    return body as T;
}

export const editorApi = {
    async contexts(): Promise<MetadataDto> {
        return asJson<MetadataDto>(await fetch('/editor/contexts'));
    },

    async rules(): Promise<RuleDto[]> {
        return asJson<RuleDto[]>(await fetch('/editor/rules'));
    },

    async validate(context: string, source: string): Promise<ValidateResponse> {
        return asJson<ValidateResponse>(
            await fetch('/editor/validate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ context, source })
            })
        );
    },

    async test(context: string, source: string, input: unknown): Promise<unknown> {
        return asJson<unknown>(
            await fetch('/editor/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ context, source, input })
            })
        );
    },

    async activate(context: string, ruleId: string, source: string): Promise<RuleDto> {
        return asJson<RuleDto>(
            await fetch('/editor/activate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ context, ruleId, source })
            })
        );
    }
};
