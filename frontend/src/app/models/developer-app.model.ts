// Defines the structure for API Product status within credentials
export interface ApiProductStatus {
  apiproduct: string;
  status: string; // e.g., "approved"
}

// Defines the structure for Credentials within a Developer App
export interface Credential {
  apiProducts: ApiProductStatus[];
  consumerKey: string;
  consumerSecret?: string; // Secret might not always be returned
  expiresAt: string; // Typically string "-1" or timestamp
  issuedAt: string; // Timestamp
  status: string; // e.g., "approved"
}

// Defines the structure for Attributes within a Developer App
export interface AppAttribute {
  name: string;
  value: string;
}

// Defines the structure for detailed Developer App data received from the backend
export interface DeveloperApp {
  appId: string;
  name: string;
  status?: string;
  createdAt?: string; // Timestamp as string
  lastModifiedAt?: string; // Timestamp as string
  developerId?: string;
  appFamily?: string;
  attributes?: AppAttribute[];
  credentials?: Credential[];
  // Add other relevant fields as needed
}
