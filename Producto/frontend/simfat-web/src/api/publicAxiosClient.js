import axios from 'axios';
import { API_BASE_URL } from './endpoints';
import { toApiError } from './apiError';

// Tokenless client for anonymous-readable endpoints (/public/*, aggregate risk
// scores, static geojson). Unlike axiosClient it never attaches an Authorization
// header and never reacts to a 401 by refreshing or clearing the auth session:
// a public page must not be able to log a user out or leak a stale token.
const publicAxiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 240000,
  headers: {
    'Content-Type': 'application/json'
  },
  withCredentials: false
});

publicAxiosClient.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(toApiError(error))
);

export default publicAxiosClient;
