// Matches the structure of the attribute object within DeveloperDetails
export interface DeveloperAttribute {
    name: string;
    value: string;
}

// Defines the structure for certificate info within DeveloperDetails
export interface DeveloperCertificate {
    id: string;
    fingerprint: string;
}

// Defines the structure for Developer Details data received from GET /api/me
export interface DeveloperDetails {
    email: string;
    firstName?: string;
    lastName?: string;
    userName?: string;
    apps?: string[];
    developerId: string;
    organizationName?: string;
    status?: string;
    createdAt?: string; // Timestamp as string
    lastModifiedAt?: string; // Timestamp as string
    attribute?: DeveloperAttribute[]; // Matching backend JSON key 'attribute'
    certificates?: DeveloperCertificate[]; // Add optional certificates array
}
