export type AccountProfile = {
  id?: number;
  email: string;
  name?: string;
  surname?: string;
  age?: number;
  gender?: string;
  height?: number;
  weight?: number;
  bloodType?: string;
  activityLevel?: string;
  goal?: string;
  allergies?: string[];
  diseases?: string[];
  password?: string;
};

export type UserProfile = {
  name?: string;
  surname?: string;
  email?: string;
  age?: number;
  gender?: string;
  height?: number;
  weight?: number;
  bloodType?: string;
  activityLevel?: string;
  goal?: string;
  allergies?: string[];
  diseases?: string[];
};

